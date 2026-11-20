# sigh-backend

Back-end do SIGH (Spring Boot 4, Java 21, PostgreSQL 17).

## Como rodar os testes

Os testes usam PostgreSQL: os de repositório e o `AutenticacaoIntegracaoTest` usam o banco local (`DB_PASSWORD`); os demais sobem um PostgreSQL com Testcontainers e precisam de Docker.

```
DB_PASSWORD=... ./mvnw verify
```

## Segurança dos dados (RNF-004, RNF-007)

### Onde o dado clínico é decifrado

O dado clínico é decifrado em **dois pontos**:
- **O back-end.** Banco, cópias de segurança e quem opera esses dois veem apenas texto cifrado. Entre o cliente e o back-end, o dado viaja em TLS, sem terminar a conexão num intermediário.
- **O serviço de IA** da triagem e do resumo (RF-003, RF-004). Ele recebe do back-end o caso e o histórico autorizado em texto claro, porque precisa ler o conteúdo. Se a IA ficar fora da máquina do back-end, ela é um componente que processa o dado e precisa ser autorizado no C4. No perfil `tls`, o back-end só aceita uma URL de IA em HTTPS (`sigh.ia.exigir-https`), salvo um serviço na própria máquina.

| Trecho | Proteção | Situação |
|---|---|---|
| Navegador → back-end | HTTPS (TLS 1.3 ou 1.2), HSTS | Testado (`TlsTest`) |
| Back-end → serviço de IA | HTTPS obrigatório no perfil `tls` | Testado (`IAConfigTest`) |
| Back-end → banco, e executor de backup → banco | TLS do PostgreSQL: `DB_URL ...?sslmode=verify-full` e `PGSSLMODE=verify-full` | Configuração de implantação, não testada aqui |
| Banco | AES-256-GCM nas colunas clínicas, feita pela aplicação | Testado (`CriptografiaEmRepousoTest`) |
| Cópias de segurança | Arquivo inteiro cifrado (OpenPGP simétrico AES-256, com verificação de integridade) | Testado (`BackupERestauracaoTest`) |

A ERS pede criptografia de ponta a ponta entre as instituições "conforme definido no C4". O C4 ainda não existe (é entrega da Unidade 2). O desenho acima é a proposta: como o prontuário é um só e fica no back-end da rede, os pontos de decifragem são o back-end e o serviço de IA, e nem transporte, nem banco, nem backup veem o texto claro. Isso precisa ser confirmado quando o C4 for feito.

### Colunas cifradas

- **Colunas:**
  - `registro_clinico.conteudo` e `codigo_cid10`;
  - `atendimento.motivo`;
  - `triagem.sintomas` e sinais vitais;
  - `resumo_historico_ia.texto`;
  - `registro_auditoria.justificativa`;
  - `analise_auditoria.descricao`;
  - `usuario.mfa_segredo`: a semente do segundo fator (RNF-005).
- **Formato gravado:** `sigh:v1:<idDaChave>:<base64(iv || texto cifrado || etiqueta)>`. Cada valor tem um IV aleatório e a etiqueta de autenticação do GCM, então um valor alterado não é decifrado.
- **Dados que já existiam em claro:** `CifragemDeDadosLegados` os cifra na inicialização. Isso inclui os dados de demonstração, que entram por SQL. Depois, a tabela passa por `VACUUM FULL`: o UPDATE deixa a versão antiga da linha nos arquivos do banco, e o backup físico copiaria esses arquivos.
- **Depois da primeira implantação com criptografia:** faça um backup completo novo e descarte as cadeias anteriores. Elas foram feitas com os dados em claro.
- **Exceção:** a justificativa da auditoria não é regravada, porque a tabela é imutável (RN-006). Só os registros novos saem cifrados.
- **O que fica em claro, e por quê:**
  - a categoria de informação: é metadado de autorização, usado nas consultas de restrição (RF-009);
  - a cor da triagem: ordena a fila (RN-005);
  - setor do leito, finalidade do acesso e datas: são metadados de operação e auditoria;
  - os dados de identificação do paciente (nome, CPF, data de nascimento): não são dado clínico, e o CPF é a chave de busca do cadastro (RN-004).
- **O tamanho do valor cifrado** acompanha o tamanho do texto. É inerente ao esquema.

### Chaves

Todas as chaves vêm do ambiente e nenhuma fica no repositório.

| Variável | Uso |
|---|---|
| `SIGH_CHAVE_DADOS` | Chave AES-256 dos dados clínicos, em Base64. Gere com `openssl rand -base64 32` |
| `SIGH_CHAVE_DADOS_ID` | Id da chave atual (padrão `1`) |
| `SIGH_CHAVES_DADOS_ANTERIORES` | Chaves antigas, só para leitura, no formato `id:chaveBase64,...`, para trocar a chave sem perder o que já foi gravado |
| `SIGH_CHAVE_BACKUP` | Chave das cópias de segurança: aleatória, com pelo menos 32 caracteres (`openssl rand -base64 32`). Vai para o gpg por arquivo temporário, nunca pela linha de comando |

Guarde `SIGH_CHAVE_DADOS` (e as anteriores) e `SIGH_CHAVE_BACKUP` num cofre, fora dos servidores de aplicação, banco e backup. Sem a chave das cópias, nenhuma cópia é restaurada. Sem a chave dos dados, a cópia restaura, mas o dado clínico fica ilegível.

### HTTPS/TLS

Ative o perfil `tls` (`SPRING_PROFILES_ACTIVE=tls`) e informe o certificado e a chave privada em PEM:

```
SIGH_TLS_CERTIFICADO=file:/caminho/certificado.pem
SIGH_TLS_CHAVE_PRIVADA=file:/caminho/chave.pem
DB_URL=jdbc:postgresql://banco:5432/sigh?sslmode=verify-full
```

O teste `TlsTest` sobe o back-end nesse perfil, com um certificado fictício. Ele confere três coisas:
- o handshake negocia TLS 1.3 e aceita TLS 1.2;
- a resposta traz o cabeçalho HSTS;
- HTTP em claro na mesma porta é recusado.

### Backup

A rotina fica em `backup/` e usa o backup incremental nativo do PostgreSQL 17 (`pg_basebackup --incremental` e `pg_combinebackup`).

| Script | Quando | O que faz |
|---|---|---|
| `backup-completo.sh` | Semanal | Abre uma nova cadeia, verifica a cópia antes de cifrar e apaga as cadeias mais antigas que `SIGH_BACKUP_RETENCAO_SEMANAS` (padrão 5), sempre preservando a atual |
| `backup-incremental.sh` | Diário | Grava só o que mudou desde o último backup da cadeia |
| `restaurar.sh <cadeia> <pasta-vazia>` | Na recuperação | Decifra conferindo a integridade, combina e verifica tudo, inclusive o WAL, com `pg_verifybackup` |

Comportamento da rotina:
- **Uma execução por vez:** `flock` impede duas execuções ao mesmo tempo.
- **Gravação segura:** cada cópia é gravada com nome temporário e renomeada no fim, e a pasta de trabalho é apagada em qualquer saída.
- **Cópia fora do servidor:** com `SIGH_BACKUP_DESTINO_EXTERNO` (uma pasta montada de outro local, como outra instituição, NFS ou armazenamento de objetos montado), cada cadeia também é copiada para lá. Assim o prontuário é recuperado mesmo se o local principal falhar (RNF-007), e o `restaurar.sh` funciona a partir dessa cópia.
- **Agendamento:** a imagem `docker/backup/Dockerfile`, sem argumentos, sobe o agendador. Ele roda o completo aos domingos e o incremental de segunda a sábado, no horário de `SIGH_BACKUP_HORARIO` (padrão `0 2`, ou seja, 2h).

**O que o servidor PostgreSQL precisa:**
- `summarize_wal = on` (exigido pelo backup incremental);
- `wal_level = replica`, que é o padrão;
- um usuário com `REPLICATION`;
- uma linha `host replication ...` no `pg_hba.conf`.

**Variáveis do executor:**
- conexão pelo padrão do libpq: `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` e `PGSSLMODE`;
- `SIGH_BACKUP_DIR` (pasta das cópias), `SIGH_CHAVE_BACKUP` e, opcionalmente, `SIGH_BACKUP_DESTINO_EXTERNO`.

**Espaço em disco:** o backup precisa de espaço temporário igual ao banco inteiro. A restauração precisa de duas a três vezes esse tamanho.

**Teste de restauração:** `BackupERestauracaoTest` usa a mesma imagem e os mesmos scripts. Ele:
1. grava um dado clínico em claro, como se fosse anterior à criptografia, e roda a cifragem que a aplicação faz ao subir;
2. faz o backup completo e dois incrementais, com inserções e uma alteração entre eles, e confere que as partes são incrementais de verdade;
3. restaura a cadeia a partir da cópia externa, num servidor novo;
4. compara a impressão digital (md5) de todas as linhas com a origem;
5. procura o dado clínico nas cópias, nas cópias decifradas e nos arquivos restaurados, e não o encontra; um controle positivo (um dado não clínico em claro) prova que a busca funciona;
6. confirma que uma cópia adulterada é recusada;
7. confirma que uma chave fraca é recusada e que a chave não aparece na linha de comando de nenhum processo;
8. confirma que o agendador sobe com a agenda e o ambiente certos.

### Limites conhecidos (cards BE-22 e BE-23)

- **C4:** a criptografia de ponta a ponta entre instituições, inclusive o papel do serviço de IA, depende do C4 (veja acima).
- **Valor cifrado sem vínculo à linha:** ele não carrega nada que o prenda à linha e à coluna onde está (dado autenticado adicional do GCM). Quem tem acesso de escrita ao banco poderia copiar o valor cifrado de um registro para outro. Corrigir isso exige que a cifra conheça o id da linha.
- **Leitura de valor legado:** um valor sem o prefixo é lido como legado em claro. Quem tem acesso de escrita ao banco poderia gravar texto claro, e ele seria lido como válido e cifrado na próxima inicialização.
- **WAL antigo:** os segmentos de WAL de antes da cifragem podem ter o dado em claro até serem reciclados. A cópia do servidor também não arquiva WAL, então a perda máxima é de um dia.
- **Chaves:** não há cofre de chaves (KMS) integrado. Chaves antigas continuam lendo, mas não há tarefa que regrave os valores com a chave nova.
- **Banco legado:** a justificativa das auditorias antigas continua como estava, porque a tabela é imutável.
- **Disponibilidade:** os 99,5% e o monitoramento real (RNF-007) são de infraestrutura e não estão neste repositório. Também não há alerta de falha do backup.
