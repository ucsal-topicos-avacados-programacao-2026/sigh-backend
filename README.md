# sigh-backend

Back-end do SIGH (Spring Boot 4, Java 21, PostgreSQL 17).

## Como rodar os testes

Os testes usam PostgreSQL: os de repositório e o `AutenticacaoIntegracaoTest` usam o banco local (`DB_PASSWORD`); os demais sobem um PostgreSQL com Testcontainers e precisam de Docker.

```
DB_PASSWORD=... ./mvnw verify
```

## Segurança dos dados (RNF-004, RNF-007)

### Onde o dado clínico é decifrado

Só o back-end decifra os dados clínicos. Banco, cópias de segurança e quem opera esses dois veem apenas texto cifrado. Entre o cliente e o back-end, o dado viaja em TLS, sem terminar a conexão num intermediário.

| Trecho | Proteção | Quem consegue ler |
|---|---|---|
| Navegador → back-end | HTTPS (TLS 1.3 ou 1.2), HSTS | Só o back-end |
| Back-end → banco | TLS no JDBC (`sslmode=verify-full`) | Só o back-end, já que as colunas clínicas vão cifradas |
| Banco e cópias | AES-256-GCM nas colunas clínicas, feita pela aplicação | Ninguém sem a chave dos dados |
| Cópias de segurança | Arquivo inteiro cifrado (AES-256 + HMAC-SHA256) | Ninguém sem a chave das cópias |

A ERS pede criptografia de ponta a ponta entre as instituições "conforme definido no C4". O C4 ainda não existe (é entrega da Unidade 2). O desenho acima é a proposta: como o prontuário é um só e fica no back-end da rede, o único ponto de decifragem é o back-end, e nem transporte, nem banco, nem backup veem o texto claro. Isso precisa ser confirmado quando o C4 for feito.

### Colunas cifradas

- **Colunas:** `registro_clinico.conteudo` e `codigo_cid10`, `atendimento.motivo`, `triagem.sintomas` e sinais vitais, `resumo_historico_ia.texto` e `registro_auditoria.justificativa`.
- **Formato gravado:** `sigh:v1:<idDaChave>:<base64(iv || texto cifrado || etiqueta)>`. Cada valor tem um IV aleatório e a etiqueta de autenticação do GCM, então um valor alterado não é decifrado.
- **Dados que já existiam em claro:** `CifragemDeDadosLegados` os cifra na inicialização. Isso inclui os dados de demonstração, que entram por SQL.
- **Exceção:** a justificativa da auditoria não é regravada, porque a tabela é imutável (RN-006). Só os registros novos saem cifrados.
- **O que fica em claro:**
  - a categoria de informação: é metadado de autorização, usado nas consultas de restrição (RF-009);
  - os dados de identificação do paciente (nome, CPF, data de nascimento): não são dado clínico, e o CPF é a chave de busca do cadastro (RN-004).

### Chaves

Todas as chaves vêm do ambiente e nenhuma fica no repositório.

| Variável | Uso |
|---|---|
| `SIGH_CHAVE_DADOS` | Chave AES-256 dos dados clínicos, em Base64. Gere com `openssl rand -base64 32` |
| `SIGH_CHAVE_DADOS_ID` | Id da chave atual (padrão `1`) |
| `SIGH_CHAVES_DADOS_ANTERIORES` | Chaves antigas, só para leitura, no formato `id:chaveBase64,...`, para trocar a chave sem perder o que já foi gravado |
| `SIGH_CHAVE_BACKUP` | Chave das cópias de segurança. Guarde fora do servidor de backup: sem ela, nenhuma cópia é restaurada |

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
| `backup-completo.sh` | Semanal | Abre uma nova cadeia e apaga as cadeias mais antigas que `SIGH_BACKUP_RETENCAO_SEMANAS` (padrão 5) |
| `backup-incremental.sh` | Diário | Grava só o que mudou desde o último backup da cadeia |
| `restaurar.sh <cadeia> <pasta-vazia>` | Na recuperação | Confere o HMAC de cada cópia, decifra, combina e verifica com `pg_verifybackup` |

A imagem `docker/backup/Dockerfile` traz as ferramentas necessárias, e `docker/backup/crontab.exemplo` tem o agendamento sugerido.

**O que o servidor PostgreSQL precisa:**
- `summarize_wal = on` (exigido pelo backup incremental);
- `wal_level = replica`, que é o padrão;
- um usuário com `REPLICATION`;
- uma linha `host replication ...` no `pg_hba.conf`.

**Variáveis do executor:**
- conexão pelo padrão do libpq: `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`;
- `SIGH_BACKUP_DIR` (pasta das cópias) e `SIGH_CHAVE_BACKUP`.

**Teste de restauração:** `BackupERestauracaoTest` usa a mesma imagem e os mesmos scripts. Ele:
1. cria dados fictícios e faz o backup completo e dois incrementais, com inserções e uma alteração entre eles;
2. restaura a cadeia num servidor novo;
3. compara a impressão digital (md5) de todas as linhas com a origem;
4. confirma que o dado clínico não aparece em claro nem nas cópias nem nos arquivos restaurados, com um controle positivo que prova que a busca funciona;
5. confirma que uma cópia adulterada é recusada.

### Limites conhecidos

- **C4:** a criptografia de ponta a ponta entre instituições depende do C4 (veja acima).
- **Disponibilidade:** os 99,5% e o monitoramento real (RNF-007) são de infraestrutura e não estão neste repositório.
- **Rotação de chave:** chaves antigas continuam lendo, mas não há tarefa que regrave os valores com a chave nova.
