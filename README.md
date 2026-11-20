# sigh-backend

Back-end do SIGH (Spring Boot 4, Java 21, PostgreSQL 17).

## Como rodar os testes

Os testes usam PostgreSQL: os de repositório e o `AutenticacaoIntegracaoTest` usam o banco local (`DB_PASSWORD`); os demais sobem um PostgreSQL com Testcontainers e precisam de Docker.

```
DB_PASSWORD=... ./mvnw verify
```

## Vínculo com a instituição (RF-012)

Médico, enfermeiro e administrativo só operam sobre dados da instituição em que têm vínculo ativo com aquele perfil. A checagem fica em cada serviço, em geral via `VinculoService`.

O `VinculoEmTodaRotaTest` lê as rotas do próprio Spring e garante que nenhuma escape. Para cada rota com perfil de médico, enfermeiro ou administrativo, e para cada um desses perfis que ela aceita, ele confere três usuários:
- sem vínculo nenhum: recebe 403;
- com vínculo só em outra instituição: recebe 403;
- com vínculo na instituição dos dados, mas encerrado ou com outro perfil: recebe 403;
- com vínculo na instituição dos dados: a mesma requisição não é recusada (nem 401 nem 403).

O último caso é o controle positivo: como a única diferença entre eles é o vínculo, o 403 vem da checagem de vínculo. O teste também confere que cada requisição cai na rota do caso.

**Rota nova:** o teste falha até a rota ganhar um caso em `casos()`. O caso só passa se o serviço checar o vínculo. Se a rota não deve exigir vínculo, justifique em `SEM_VINCULO`. Hoje a única exceção é a busca de leitos (`GET /leitos`): ela é aberta à rede para ver vagas em transferências e não traz dado de paciente. O teste também falha quando:
- alguma rota fica sem `@PreAuthorize`, salvo as de autenticação;
- a regra de acesso não é `hasRole` ou `hasAnyRole`, que são as que ele sabe ler;
- a rota não declara o método HTTP.

## Transferência entre instituições (RF-006)

A ERS (Processo 2, Gestão de Leitos) diz que, sem leito na unidade e com leito em outra da rede, "solicita-se transferência para outra unidade da rede", e a unidade de destino executa o próprio processo. Por isso (BE-21):
- **Na mesma instituição:** a transferência entre leitos continua direta (`POST /internacoes/{id}/transferencia`). Para leito de outra instituição, essa rota recusa.
- **Para outra instituição:** o administrativo da origem pede (`POST /internacoes/{id}/solicitacoes-transferencia`, com a instituição de destino e a categoria do leito). O paciente continua no leito atual.
- **O destino decide:**
  - o administrativo do destino vê os pedidos pendentes (`GET /solicitacoes-transferencia/recebidas?instituicaoId=`, do mais antigo para o mais novo);
  - ele aceita escolhendo um leito livre dele, da categoria pedida (`POST /solicitacoes-transferencia/{id}/aceite`). Aí o paciente ocupa esse leito, e o da origem vai para higienização;
  - ou recusa com motivo (`.../recusa`), e o paciente fica na origem.
- **A origem** acompanha os pedidos que fez, com o status e o motivo da recusa (`GET /solicitacoes-transferencia/enviadas?instituicaoId=`). Ela pode cancelar enquanto o pedido estiver pendente (`.../cancelamento`).
- **Alta:** a alta na origem cancela o pedido pendente. Nesse caso, o decisor registrado é quem deu a alta, sem motivo.
- **Concorrência:** a origem e o destino podem agir ao mesmo tempo sobre o mesmo paciente. Toda operação que muda o leito de uma internação trava a internação primeiro, sempre na mesma ordem: internação, pedido, leito. Assim, o paciente nunca fica com dois leitos, nem com leito ocupado depois da alta.
- **Credenciamento:** o destino precisa estar credenciado tanto no pedido quanto no aceite.
- **Um pedido pendente por internação:** a regra vale também no banco, com um índice único parcial.
- **O prontuário continua único.** A internação é a mesma; só mudam o leito e a instituição. O pedido mostra ao destino só o nome do paciente.

Os testes estão em `TransferenciaEntreInstituicoesTest`, e as rotas novas, no `VinculoEmTodaRotaTest`.

## Acesso de emergência (RF-010, RF-011)

Em emergência, médico ou enfermeiro com vínculo no hospital do atendimento vê também o que o paciente restringiu (`POST /atendimentos/{id}/historico/emergencia`). Antes de qualquer dado aparecer, a justificativa (mínimo de 20 caracteres) é gravada na auditoria, e o paciente vê o acesso marcado como emergência.

**Controles contra abuso (BE-18 e BE-24).** Decididos pelo grupo em 20/11/2026:
1. **Gravidade:** só vale se a classificação de risco confirmada mais recente do atendimento for vermelha ou laranja. Se o paciente melhorar e for reclassificado, perde a emergência. Se piorar de novo, recupera. O enfermeiro pode reclassificar o risco a qualquer momento até o atendimento ser encerrado, inclusive com o paciente já com o médico ou internado, enquanto o atendimento de origem continuar aberto (BE-24). A reclassificação não devolve o paciente para a fila de triagem. Cada nova classificação vermelha ou laranja recomeça a janela de 12 horas. Por isso, dois profissionais combinados conseguem renovar a janela sem limite, e a fila e a auditoria registram cada passo. Isso foi aceito na decisão.
2. **Janela:** só vale até 12 horas depois dessa classificação (`sigh.emergencia.janela`).
3. **Revisão obrigatória:** todo acesso de emergência entra na fila do auditor (`GET /auditoria/emergencias/pendentes`, do mais antigo para o mais novo). Ele só sai dela quando o auditor registra uma análise conclusiva (regular, indevido ou inconclusivo) em `POST /auditoria/registros/{id}/analises`, seja um parecer ou um alerta. Uma análise "pendente" não o tira da fila.

4. **Duas pessoas (BE-24):** quem confirmou a classificação vermelha ou laranja não pode usar o acesso de emergência com base nela. Precisa ser outro profissional, para que reclassificar não vire atalho para a emergência.

Toda tentativa recusada por esses controles fica na auditoria, com um motivo curto e fixo na finalidade. São três motivos:
- "Tentativa de acesso de emergência recusada: sem classificação vermelha ou laranja confirmada";
- "... fora da janela depois da classificação";
- "... quem confirmou a classificação tentou usá-la".

Ela fica numa transação própria, para não ser desfeita junto com a recusa. Se essa gravação falhar, o acesso continua negado. O auditor vê a tentativa em `/auditoria/registros`, e o paciente, nos acessos ao seu prontuário. Ela não entra na fila de emergências nem nos indicadores por instituição, porque nenhum dado foi visto. Uma classificação sem quem a confirmou registrado não libera emergência, e uma triagem de atendimento já encerrado não pode ser confirmada.

Não há teto de acessos por profissional: um teto poderia bloquear uma emergência de verdade. O auditor também vê os indicadores por instituição. Os controles estão provados em `EmergenciaControladaTest`.

## Sugestão de risco e resumo por IA (RF-003, RF-004)

O serviço de IA padrão é o Ollama, na mesma máquina, com o modelo `qwen3:8b`. Ele atende um pedido por vez. Um resumo leva cerca de 23 s e uma sugestão de risco, cerca de 10 s (medidos no TST-04, em `carga/README.md`).

**Sugestão de risco (enfermeiro, na triagem):** `POST /triagens/{id}/sugestao` responde na hora, com a cor sugerida. A API espera a IA até `sigh.ia.tempo-limite` (60 s). No Ollama, a sugestão espera os pedidos que chegaram antes: no máximo um resumo, porque eles vão um por vez, mas também as outras sugestões. Uma sugestão com um resumo à frente leva cerca de 33 s. Com muitas sugestões ao mesmo tempo, as últimas podem passar de 60 s. Se a IA não responder a tempo, a API devolve 503 com o aviso, e o enfermeiro classifica sem a sugestão.

**Resumo do histórico (médico), em segundo plano (BE-25):**
1. `POST /atendimentos/{id}/resumos` volta na hora com 202, e o resumo fica `SOLICITADO`, com o aviso de que está em geração. A resposta nunca traz o texto: ele só sai na consulta, que é auditada.
2. A API gera os resumos em segundo plano, um por vez, porque pedidos em paralelo só esperariam no Ollama e estourariam o tempo-limite. Antes de mandar o histórico à IA, ela confere de novo se o médico ainda tem vínculo e se o paciente não restringiu alguma das fontes enquanto o pedido esperava na fila (RF-009). Se algo mudou, o resumo vira `FALHOU` e nada vai à IA.
3. O médico consulta `GET /resumos/{id}` até o status mudar:
   - `GERADO`, com o texto;
   - ou `FALHOU`, com o aviso para seguir pelos registros originais ou pedir de novo.

   A tela pode consultar a cada poucos segundos. Enquanto não há texto, a consulta não mostra dado clínico e não entra na auditoria. Quando o texto aparece, cada consulta entra.

Detalhes da fila de resumos:
- **Tamanho:** a fila guarda até `sigh.ia.fila-maxima-resumos` pedidos (50). Com ela cheia, o pedido é recusado na hora, com 503, e o resumo fica `FALHOU`.
- **Tempo-limite:** cada resumo espera a IA até `sigh.ia.tempo-limite-resumo` (120 s). Conta a partir do envio à IA, não o tempo na fila da API. Inclui, porém, a espera no próprio Ollama atrás de sugestões de risco.
- **Nunca preso "em geração":** a fila sabe quais resumos estão nela. Se a tarefa terminar sem conseguir gravar o resultado (o banco fora, por exemplo), a próxima consulta mostra o resumo como `FALHOU`.
- **Memória e instância única:** a fila fica na memória. Se a aplicação parar com resumos nela, eles viram `FALHOU` ao subir (`RecuperacaoDosResumos`), antes de o servidor aceitar pedidos. Isso supõe uma instância só da aplicação. Pare a instância anterior antes de subir a nova: senão, a nova marca como falhos os resumos que a anterior ainda gera, e o texto que a anterior conseguir depois é descartado.
- **Front-end:** a tela do médico precisa pedir o resumo e consultar `GET /resumos/{id}` até sair de `SOLICITADO`. Antes do BE-25, o pedido devolvia o texto direto (201).

**Modelo carregado:** ao subir, a API pede à IA uma resposta mínima pela fila dos resumos, para o modelo carregar antes do primeiro pedido (`sigh.ia.aquecer-ao-subir`). O Ollama descarrega o modelo depois de 5 min parado. Para mantê-lo carregado, suba-o com:

```
OLLAMA_KEEP_ALIVE=-1 ollama serve
ollama pull qwen3:8b   # uma vez
```

## Integração com o front-end (FE-02)

O front-end (`sigh-frontend`) roda em outra origem e chama a API direto, com o token no cabeçalho `Authorization`. Não há proxy no meio, que seria um intermediário lendo o tráfego (RNF-004).

**Quem está logado:** `GET /usuarios/eu` devolve o login, o nome, os perfis e os vínculos ativos agora. Cada vínculo traz a instituição e os perfis habilitados nela, e dois vínculos na mesma instituição aparecem juntos.

- **Perfis de cada vínculo:** só os que o usuário tem de fato, porque operar exige o perfil e o vínculo que o habilita. Uma instituição em que nenhum perfil sobra não aparece.
- **Uso no front:** é com isso que ele monta o menu por perfil e o seletor de instituição.
- **Ordem:** os vínculos vêm ordenados pelo nome da instituição, com as regras do português ("Ébano" antes de "Zona"). A rota não exige vínculo, porque só mostra dados do próprio usuário, e está justificada no `VinculoEmTodaRotaTest`.

**CORS:**
- **Origens:** só as de `SIGH_CORS_ORIGENS`, separadas por vírgula. Por exemplo, `https://localhost:5173,https://localhost:4173`.
- **O que é aceito:** só os métodos que a API tem (GET, POST e PATCH) e os cabeçalhos `Authorization` e `Content-Type`, sem cookies (`allowCredentials` desligado).
- **Onde fica:** na cadeia do Spring Security. Assim, até o 401 de uma sessão expirada chega ao front com o cabeçalho de CORS.
- **Sem a variável:** não há CORS, e o navegador recusa a chamada de outra origem.
- **Curinga:** `*` não é aceito, porque a API traz dado clínico.

Os dois pontos estão provados em `UsuarioLogadoTest` e `CorsTest`.

## Desempenho e carga (RNF-001, RNF-002)

O relatório do teste de carga, da medição da IA, da inspeção da criptografia e do ensaio de restauração está em [`carga/README.md`](carga/README.md), com os scripts para reproduzir.

## Segurança dos dados (RNF-004, RNF-007)

### Onde o dado clínico é decifrado

Os hospitais da rede não trocam prontuário entre si. O prontuário é um só e fica na API da rede, e cada hospital acessa pelo navegador. Por isso, a ponta a ponta vai do navegador do profissional autorizado até a API, e da API até o serviço de IA. Pelo RNF-004, só os componentes autorizados leem o dado clínico em claro, e nenhum intermediário de transporte lê.

**Quem lê o dado clínico em claro:**

| Componente | Lê em claro? | Por quê | Prova |
|---|---|---|---|
| API (este back-end) | Sim | Só ela tem `SIGH_CHAVE_DADOS`: decifra ao carregar e cifra antes de gravar | `CriptografiaEmRepousoTest` |
| Serviço de IA da triagem e do resumo (RF-003, RF-004) | Sim | Recebe da API o caso e o histórico autorizado, porque precisa ler o conteúdo | `ClienteIATest` (o que é enviado), `IAConfigTest` (para onde) |
| Navegador do profissional | Sim, só o que a API devolve | A API confere perfil, vínculo com a instituição e consentimento antes de responder | `VinculoEmTodaRotaTest`, `EmergenciaControladaTest` |
| Rede entre navegador e API, e entre API e banco | Não | TLS terminado na própria API e no próprio banco, com o certificado conferido | `TlsTest` |
| Caminho da API até a IA | Não | Na própria máquina (o padrão), o pedido não sai para a rede: vai pelo loopback, em HTTP, sem passar por proxy. Num host autorizado, vai por HTTPS, com a conferência padrão da JDK (certificado e nome do host), que não tem teste próprio aqui | `IAConfigTest` (destino, sem redirecionamento e sem proxy) |
| Banco e quem o administra | Não | As colunas clínicas estão cifradas, e o banco nunca recebe a chave dos dados | `CriptografiaEmRepousoTest` |
| Cópias de segurança, WAL arquivado e destino externo | Não | O arquivo é cifrado com `SIGH_CHAVE_BACKUP`, e dentro dele as colunas clínicas continuam cifradas com a chave dos dados | `BackupERestauracaoTest` |

**A IA só recebe dado num destino autorizado.** No perfil `tls` (`sigh.ia.restringir-destino`), a API não sobe se `sigh.ia.url` apontar para outro lugar. O destino tem de ser:
- um serviço na própria máquina (`localhost`, `127.0.0.1` ou `[::1]`), que é o padrão, com o Ollama local;
- ou um host listado em `SIGH_IA_HOSTS_AUTORIZADOS` (separados por vírgula), e então só por HTTPS.

A API também não segue redirecionamento da IA, porque um redirecionamento levaria o dado a um destino que não passou por essa conferência. E não passa por proxy configurado na JVM, que veria em claro o tráfego HTTP para a IA na própria máquina. Para pôr a IA em outra máquina, inclua o host na lista e no C4, como componente autorizado.

**Um proxy ou balanceador** que termine o TLS antes da API vira ponto de decifragem. A implantação deste trabalho não tem nenhum. Se um for colocado, ele precisa entrar no C4.

**Quem guarda cada chave:**

| Chave | Fica com | Quem a tem consegue ler |
|---|---|---|
| `SIGH_CHAVE_DADOS` (e as anteriores) | Só a API | O dado clínico. É o que faz da API um ponto de decifragem |
| `SIGH_TLS_CHAVE_PRIVADA` | Só a API | O tráfego que chega à API |
| Chave TLS do banco (`SIGH_TLS_CHAVE_BANCO`) | Só o servidor do banco | O tráfego que chega ao banco, onde o dado clínico já vem cifrado |
| `SIGH_CHAVE_BACKUP` | O executor de backup e o servidor do banco, para arquivar o WAL | O conteúdo das cópias, que é o mesmo do banco: o dado clínico continua cifrado |
| Chave TLS do serviço de IA, se ele estiver em outra máquina | Só o serviço de IA | O tráfego que chega à IA, que já é um ponto de decifragem |
| `SIGH_IA_CHAVE` | A API e o serviço de IA, que a confere | Nada: só identifica a API para a IA, e não cifra dado |

Os valores ficam fora do repositório (veja "Chaves").

**Transporte, trecho a trecho:**

| Trecho | Proteção | Situação |
|---|---|---|
| Navegador → back-end | HTTPS (TLS 1.3 ou 1.2), HSTS | Testado (`TlsTest`) |
| Back-end → serviço de IA | No perfil `tls`, só na própria máquina (loopback, sem TLS) ou num host autorizado por HTTPS; sem seguir redirecionamento e sem proxy | Testado (`IAConfigTest`, `TlsTest`) |
| Back-end → banco | TLS com o certificado do servidor conferido (`sslmode=verify-full`); no perfil `tls`, sem isso a aplicação não sobe | Testado (`TlsTest`) |
| Executor de backup → banco | TLS com o certificado conferido (`PGSSLMODE=verify-full`); sem isso o backup não roda | Testado (`BackupERestauracaoTest`) |
| Banco | Só aceita conexão com TLS; AES-256-GCM nas colunas clínicas, feita pela aplicação, com o valor preso à linha | Testado (`TlsTest`, `CriptografiaEmRepousoTest`) |
| Cópias de segurança e WAL arquivado | Arquivo inteiro cifrado (OpenPGP simétrico AES-256, com verificação de integridade) | Testado (`BackupERestauracaoTest`) |

O modelo C4 da Unidade 2 deve mostrar estes mesmos pontos de decifragem e estas mesmas chaves (card BE-23).

### Colunas cifradas

- **Colunas:**
  - `registro_clinico.conteudo` e `codigo_cid10`;
  - `atendimento.motivo`;
  - `triagem.sintomas` e sinais vitais;
  - `resumo_historico_ia.texto`;
  - `registro_auditoria.justificativa`;
  - `analise_auditoria.descricao`;
  - `usuario.mfa_segredo`: a semente do segundo fator (RNF-005).
- **Formato gravado:** `sigh:v2:<idDaChave>:<base64(iv || texto cifrado || etiqueta)>`. Cada valor tem um IV aleatório e a etiqueta de autenticação do GCM, então um valor alterado não é decifrado.
- **Valor preso à linha:** a tabela, a coluna e o id da linha entram como dado autenticado adicional do GCM. Um valor cifrado copiado para outra linha ou outra coluna, por quem tem escrita no banco, não é decifrado. A leitura é recusada, com erro 500 genérico, e o log registra a coluna e o id da linha, nunca o conteúdo.
- **Como funciona na aplicação:** cada entidade tem o campo em claro, transiente e marcado com `@Cifrado`, e o campo gravado. O listener `CifragemDasEntidades` cifra antes do INSERT e do UPDATE e decifra ao carregar. Para cifrar um campo novo:
  1. marque o campo em claro com `@Transient @Cifrado(gravadoEm = "...")`;
  2. crie o campo gravado como `String`, com `@Column(name = ...)`;
  3. escreva um setter que ponha `Cifrado.PENDENTE` no campo gravado. É isso que faz o Hibernate perceber a mudança, inclusive de nulo para preenchido;
  4. ponha `@EntityListeners(CifragemDasEntidades.class)` na entidade;
  5. inclua a coluna na lista do `CriptografiaEmRepousoTest`.

  Ao subir, a aplicação confere essas peças e não sobe se faltar alguma (`CamposCifrados`). A manutenção usa a mesma lista de campos. Uma consulta JPQL não seleciona o campo cifrado nem passa a entidade para um `select new`: o campo em claro só é preenchido no `@PostLoad`, depois do construtor do DTO. Busque a entidade e monte o DTO depois.
- **O que fica em claro, e por quê:**
  - a categoria de informação: é metadado de autorização, usado nas consultas de restrição (RF-009);
  - a cor da triagem: ordena a fila (RN-005);
  - setor do leito, finalidade do acesso e datas: são metadados de operação e auditoria;
  - os dados de identificação do paciente (nome, CPF, data de nascimento): não são dado clínico, e o CPF é a chave de busca do cadastro (RN-004).
- **O tamanho do valor cifrado** acompanha o tamanho do texto. É inerente ao esquema.

### Manutenção ao subir (`ManutencaoDaCifragem`)

Roda ao subir a aplicação, antes de o servidor web aceitar requisições. Uma trava do PostgreSQL impede duas instâncias de rodarem a manutenção ao mesmo tempo.

1. **VACUUM pendente.** Refaz o `VACUUM FULL` de uma tabela regravada se a aplicação caiu antes dele. A tabela entra na fila (`cifragem_vacuum_pendente`) na mesma transação que a regrava. Ela só sai da fila quando o VACUUM pôde mesmo limpar:
   - o usuário do banco precisa ser dono da tabela e poder fazer a manutenção das estatísticas, que estão em `pg_statistic`. Para isso, sem ser superusuário: `GRANT pg_maintain TO <usuário da aplicação>` (PostgreSQL 17);
   - nenhuma outra sessão pode segurar um retrato antigo do banco;
   - o lock exclusivo tem tempo limite de 30 s.

   O que não pôde ser feito fica para a próxima inicialização, com aviso no log, e a saúde da aplicação fica DOWN enquanto houver tabela na fila.
2. **Migração, só na primeira vez.** Cifra no formato atual o que está em claro e o que está no formato `sigh:v1` do BE-09, sem vínculo à linha. O texto em claro vem de dados anteriores à criptografia e dos dados de demonstração, que entram por SQL. Depois, grava a marca `controle_cifragem`.
3. **Troca de chave.** Regrava com a chave atual os valores das chaves anteriores.
4. **Detecção.** Depois da migração, um valor em claro ou no formato antigo só pode ter sido gravado fora da aplicação. Ele não é cifrado: o log registra a coluna e a contagem, a leitura dele é recusada e a saúde da aplicação (`/actuator/health`, componente `cifragem`) fica DOWN.

Toda tabela regravada passa por `VACUUM (FULL, ANALYZE)`, seguido de `VACUUM FULL pg_statistic`. O UPDATE deixa a versão antiga da linha, em claro ou com a chave antiga, nos arquivos do banco, e as estatísticas da coluna guardam amostras dos valores. O backup físico copiaria esses arquivos.

**Exceção da auditoria:** a justificativa não é regravada, porque a tabela é imutável (RN-006). A migração V6 lista as linhas que já existiam (`auditoria_justificativa_legada`, uma lista que não aceita alteração), e só elas continuam lendo o texto claro e o formato antigo. Uma auditoria inserida depois, fora da aplicação, com justificativa sem a cifra atual, é detectada e recusada como as outras colunas.

**Depois da primeira implantação de cada versão da criptografia, e depois de cada troca de chave:**
1. Pare todas as instâncias da versão anterior antes de subir a nova. O que uma instância antiga gravar depois da migração fica fora do formato e é recusado. Para refazer a migração, apague a linha de `controle_cifragem`.
2. Rode `limpar-wal-legado.sh` no container do banco (`docker exec <container do banco> limpar-wal-legado.sh`). Ele tira do servidor os segmentos de WAL que guardam o dado de antes da cifragem, ou o cifrado com a chave antiga (BE-26):
   - desliga o reaproveitamento de segmentos;
   - grava WAL de enchimento até passar do segmento mais adiantado: do atual em diante, inclusive os reaproveitados para uso futuro, os segmentos são sobrescritos por inteiro;
   - espera o arquivamento e faz checkpoints até nenhum segmento de antes existir mais. Os anteriores ao atual são apagados, não sobrescritos (veja "Limites conhecidos").

   Ele sai com erro, dizendo o motivo, se algum continuar: arquivamento parado, slot de replicação, `wal_keep_size` ou o resumo do WAL atrasado. Antes de rodar:
   - pause o agendador de backup, porque um backup em andamento cria um slot que retém WAL;
   - confira o espaço livre no volume do banco. O enchimento grava o mesmo volume no WAL e nos dados, até passar do segmento mais adiantado (no máximo cerca de `max_wal_size`). O script confere o espaço e para antes, se faltar.
3. Rode `backup-completo.sh --descartar-anteriores`. Ele apaga as cadeias anteriores, aqui e no destino externo, e o WAL arquivado anterior, que guardam os valores antigos.

### Chaves

Todas as chaves vêm do ambiente e nenhuma fica no repositório.

| Variável | Uso |
|---|---|
| `SIGH_CHAVE_DADOS` | Chave AES-256 dos dados clínicos, em Base64. Gere com `openssl rand -base64 32` |
| `SIGH_CHAVE_DADOS_ID` | Id da chave atual (padrão `1`) |
| `SIGH_CHAVES_DADOS_ANTERIORES` | Chaves antigas, só para leitura, no formato `id:chaveBase64,...` |
| `SIGH_CHAVE_BACKUP` | Chave das cópias e do WAL arquivado: aleatória, com pelo menos 32 caracteres (`openssl rand -base64 32`). Vai para o gpg por arquivo temporário, nunca pela linha de comando |

**Trocar a chave dos dados:**
1. Gere a nova chave.
2. Mova a atual para `SIGH_CHAVES_DADOS_ANTERIORES` com o id dela.
3. Ponha a nova em `SIGH_CHAVE_DADOS` com um id novo e reinicie a aplicação. Ao subir, ela regrava tudo com a chave nova. A justificativa das auditorias antigas não é regravada.
4. Rode `backup-completo.sh --descartar-anteriores`.
5. Se o log não acusar valor de chave anterior pendente, a chave antiga pode sair do ambiente.

O segredo do segundo fator também é cifrado com `SIGH_CHAVE_DADOS`. Se a chave faltar, ou for trocada sem constar em `SIGH_CHAVES_DADOS_ANTERIORES`, o login com segundo fator deixa de funcionar.

**Onde guardar as chaves (decisão do BE-22):** não há cofre de chaves (KMS). Por ser um trabalho acadêmico rodado localmente, as chaves ficam num arquivo de ambiente fora do repositório, legível só pelo dono (`chmod 600`). Em produção, elas iriam para um cofre, fora dos servidores de aplicação, banco e backup. Sem a chave das cópias, nenhuma cópia é restaurada. Sem a chave dos dados, a cópia restaura, mas o dado clínico fica ilegível.

### HTTPS/TLS

Ative o perfil `tls` (`SPRING_PROFILES_ACTIVE=tls`) e informe o certificado e a chave privada em PEM, além da URL do banco com TLS:

```
SIGH_TLS_CERTIFICADO=file:/caminho/certificado.pem
SIGH_TLS_CHAVE_PRIVADA=file:/caminho/chave.pem
DB_URL=jdbc:postgresql://banco:5432/sigh?sslmode=verify-full&sslrootcert=/caminho/autoridade-do-banco.pem
```

No perfil `tls`, a aplicação não sobe, e nem chega a migrar o banco, quando:
- a URL do banco não pede `sslmode=verify-full`;
- a URL troca `sslfactory` ou `sslhostnameverifier`, o que poderia desligar a conferência do certificado;
- a sessão aberta não está cifrada.

Quem faz essa conferência é `ConexaoSeguraComOBanco`.

**O banco com TLS:** a imagem `docker/backup` também sobe o PostgreSQL, com `iniciar-banco.sh`. Ela precisa das variáveis da imagem oficial (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`) e de `SIGH_TLS_CERTIFICADO_BANCO` e `SIGH_TLS_CHAVE_BANCO`, que são os caminhos dos arquivos PEM montados no container. Ela sobe:
- só com TLS 1.2 ou 1.3 e com `pg_hba-tls.conf`, que recusa qualquer conexão sem TLS, inclusive a de replicação;
- com `summarize_wal` ligado, para o backup incremental;
- com `wal_recycle` desligado: um segmento de WAL antigo é apagado, e não renomeado para uso futuro com o conteúdo antigo dentro;
- com `SIGH_WAL_DIR`, arquivando o WAL cifrado.

O teste `TlsTest` sobe o back-end nesse perfil contra esse banco, com certificados fictícios. Ele confere:
- o handshake negocia TLS 1.3 e aceita TLS 1.2, e a resposta traz o cabeçalho HSTS;
- HTTP em claro na mesma porta é recusado;
- a sessão da aplicação com o banco usa TLS 1.3;
- o banco recusa conexão sem TLS e o cliente recusa um certificado de banco em que não confia;
- sem `verify-full`, a aplicação de verdade não sobe e o banco fica sem migração nenhuma;
- a aplicação de verdade também não sobe com a IA num host fora de `SIGH_IA_HOSTS_AUTORIZADOS`, nem com um host autorizado sem HTTPS.

### Backup

A rotina fica em `backup/` e usa o backup incremental nativo do PostgreSQL 17 (`pg_basebackup --incremental` e `pg_combinebackup`).

| Script | Quando | O que faz |
|---|---|---|
| `backup-completo.sh [--descartar-anteriores]` | Semanal | Abre uma nova cadeia, verifica a cópia antes de cifrar e apaga as cadeias mais antigas que `SIGH_BACKUP_RETENCAO_SEMANAS` (padrão 5), sempre preservando a atual. A retenção vale aqui e no destino externo. Também apaga, em cada lugar, o WAL arquivado anterior à cadeia mais antiga que ficou ali. Com `--descartar-anteriores`, apaga todas as outras cadeias e o WAL anterior a esta (veja "Manutenção ao subir") |
| `backup-incremental.sh` | Diário | Grava só o que mudou desde o último backup da cadeia. Sem nenhum completo ainda, faz o completo |
| `arquivar-wal.sh` | A cada segmento de WAL (no servidor) | Comprime e cifra o segmento em `SIGH_WAL_DIR`. Grava em disco (`sync`) antes de confirmar ao servidor. Nunca sobrescreve um segmento diferente já arquivado, e uma sobra de queda não o trava |
| `restaurar.sh <cadeia> <pasta-vazia> [pasta-do-wal]` | Na recuperação | Decifra conferindo a integridade, combina e verifica tudo, inclusive o WAL, com `pg_verifybackup`. Com a pasta do WAL, o servidor aplica ao subir o que mudou depois do último backup (`recuperar-wal.sh`). Um segmento alterado, a chave ausente ou a pasta inacessível interrompem a restauração, em vez de terminá-la ali em silêncio. A cópia restaurada sobe sem arquivar WAL |
| `verificar-backup.sh` | `HEALTHCHECK` da imagem, a cada 10 min | O alerta: veja abaixo |
| `limpar-wal-legado.sh` | Uma vez, no container do banco, depois da migração da cifragem e de cada troca de chave | Tira do servidor os segmentos de WAL com o dado de antes (veja "Manutenção ao subir"). Provado em `LimpezaDoWalLegadoTest` |

Comportamento da rotina:
- **Só com TLS:** o backup só conecta ao banco com `PGSSLMODE=verify-full`, conferindo o certificado do servidor (`PGSSLROOTCERT`).
- **Uma execução por vez:** `flock` impede duas execuções ao mesmo tempo.
- **Gravação segura:** cada cópia é gravada com nome temporário e renomeada no fim, e a pasta de trabalho é apagada em qualquer saída.
- **Cópia fora do servidor:** com `SIGH_BACKUP_DESTINO_EXTERNO` (uma pasta montada de outro local, como outra instituição, NFS ou armazenamento de objetos montado), cada cadeia também é copiada para lá. Se o executor enxergar `SIGH_WAL_DIR`, o WAL arquivado também vai, para `wal/`. Assim o prontuário é recuperado mesmo se o local principal falhar (RNF-007), e o `restaurar.sh` funciona a partir dessa cópia. `SIGH_WAL_DIR` também deve ser uma pasta de outro disco ou local.
- **Perda máxima:** com o WAL arquivado, a perda máxima é o intervalo de `SIGH_WAL_INTERVALO` (padrão 300 s), e não mais um dia.
- **Agendamento:** a imagem `docker/backup/Dockerfile`, sem argumentos, sobe o agendador. Numa instalação nova, sem nenhum backup, ele roda um completo na hora. Depois, roda o completo aos domingos e o incremental de segunda a sábado, no horário de `SIGH_BACKUP_HORARIO` (padrão `0 2`, ou seja, 2h), e manda a saída de cada execução para o log do container. O horário é em UTC, porque a imagem não tem fuso configurado: 2h UTC são 23h em Salvador. Os valores das variáveis não podem ter quebra de linha; por isso, gere chaves com `openssl rand -base64 32`, que cabe numa linha só.
- **Alerta:** o container fica `unhealthy` (`docker ps`, `docker inspect`) quando:
  - a última execução agendada falhou;
  - a última execução foi pulada porque outra estava em andamento;
  - não há backup concluído nas últimas `SIGH_BACKUP_MAX_HORAS` (padrão 26);
  - no container do banco, o arquivamento de WAL está falhando.

  A mensagem sai em `docker inspect --format '{{json .State.Health}}'`. Por ser local, o alerta é esse estado do Docker. Mandar e-mail ou chamar um sistema de monitoramento fica para quando houver um.

**O que o executor precisa:**
- conexão pelo padrão do libpq: `PGHOST`, `PGPORT`, `PGUSER` (com `REPLICATION`), `PGPASSWORD`, `PGSSLMODE=verify-full` e `PGSSLROOTCERT`;
- `SIGH_BACKUP_DIR` (pasta das cópias), `SIGH_CHAVE_BACKUP` e, opcionalmente, `SIGH_BACKUP_DESTINO_EXTERNO` e `SIGH_WAL_DIR`. Esta é a mesma pasta do WAL arquivado do servidor, para a cópia externa e a limpeza.

**Espaço em disco:** o backup precisa de espaço temporário igual ao banco inteiro. A restauração precisa de duas a três vezes esse tamanho.

**Teste de restauração:** `BackupERestauracaoTest` usa a mesma imagem e os mesmos scripts, para o banco (com TLS e WAL arquivado) e para o executor. Ele:
1. grava um dado clínico em claro, como se fosse anterior à criptografia, e roda a manutenção que a aplicação faz ao subir. Confere que o WAL arquivado guarda o dado em claro, como controle, e que ele some com o completo `--descartar-anteriores`;
2. faz o backup completo e dois incrementais, com inserções e uma alteração entre eles, e confere que as partes são incrementais de verdade;
3. grava mais um paciente depois do último backup e espera o WAL ser arquivado;
4. restaura a cadeia a partir da cópia externa, com o WAL, num servidor novo;
5. compara a impressão digital (md5) de todas as linhas com a origem, inclusive o paciente que só voltou pelo WAL;
6. procura o dado clínico nas cópias, nas cópias e no WAL decifrados e nos arquivos restaurados, e não o encontra; um controle positivo (um dado não clínico em claro) prova que a busca funciona;
7. confirma que uma cópia adulterada é recusada;
8. confirma que uma chave fraca é recusada e que a chave não aparece na linha de comando de nenhum processo;
9. confirma que o banco recusa conexão sem TLS, inclusive a de replicação, e que o backup não roda sem `verify-full`;
10. confirma que o alerta acusa:
    - backup que falhou, que foi pulado ou que está atrasado;
    - arquivamento de WAL falhando;
11. confirma como o WAL é arquivado e recuperado:
    - uma sobra de queda não trava o arquivamento;
    - um segmento diferente não sobrescreve o já arquivado;
    - um segmento alterado interrompe a restauração;
12. confirma que o agendador sobe com a agenda e o ambiente certos e faz o primeiro completo.

### Limites conhecidos

- **C4 (card BE-23):** os pontos de decifragem e as chaves acima ainda precisam aparecer no modelo C4 da Unidade 2.
- **Cofre de chaves:** decidido não usar no trabalho, que roda localmente (veja "Chaves").
- **Valor copiado de outra linha:** é recusado na leitura, mas a manutenção ao subir não decifra todos os valores para procurá-los. Ela só conta os que estão fora do formato.
- **Valor antigo da mesma linha:** o vínculo prende o valor à linha e à coluna, não a uma versão. Recolocar um valor cifrado mais antigo da mesma linha, tirado de um backup, é aceito.
- **Leitura recusada derruba a consulta inteira:** uma linha adulterada faz falhar toda consulta que a carrega. Isso vale para o histórico do paciente e para a fila. Vale também para o segredo do segundo fator: um `usuario.mfa_segredo` adulterado derruba o login desse usuário e toda consulta que o carrega como autor, ator ou registrador, como a listagem do auditor. Isso é de propósito: o prontuário não aparece pela metade sem aviso. A correção é restaurar o valor a partir do backup.
- **Auditoria antiga:** a justificativa das auditorias listadas na migração continua como estava, em claro ou sem vínculo à linha, porque a tabela é imutável. Entre essas linhas, um valor `sigh:v1` copiado de uma para outra seria aceito.
- **WAL de antes da cifragem:** os segmentos de WAL do servidor de antes da migração têm o dado em claro até o `limpar-wal-legado.sh` rodar. Depois dele, nenhum arquivo de `pg_wal` tem o dado, mas há limites:
  - os segmentos anteriores ao atual, onde estão o INSERT legado e a migração, são apagados, não sobrescritos. O conteúdo pode continuar nos blocos livres do disco até ser reaproveitado, em qualquer tipo de disco;
  - réplicas e outras cópias do servidor ficam fora do alcance do script. Uma réplica sem slot nem `restore_command` pode ficar para trás com o enchimento;
  - a justificativa das auditorias antigas, mantida em claro de propósito (veja acima), volta ao WAL quando uma página dela é gravada de novo por inteiro.
- **Chave do WAL no servidor do banco:** o arquivamento roda no servidor do banco, então `SIGH_CHAVE_BACKUP` fica no ambiente dele, e um superusuário do banco consegue lê-la. Quem administra o servidor já tem acesso ao banco em claro. A aplicação não deve conectar como superusuário.
- **Cópia restaurada:** ela sobe com a configuração de autenticação padrão e sem arquivar WAL. Para pô-la em uso, suba-a com `iniciar-banco.sh`, que liga o TLS obrigatório, e com outro `SIGH_WAL_DIR`. Com o mesmo, a nova linha do tempo dela desviaria as próximas restaurações.
- **Alerta da aplicação:** o `/actuator/health` exige login, como toda rota, e a detecção de valor fora do formato só é refeita quando a aplicação sobe. A fila de VACUUM é conferida a cada consulta. O alerta sem login é o do container de backup, pelo HEALTHCHECK.
- **Embutido em coleção:** campo `@Cifrado` dentro de um `@Embeddable` usado em `@ElementCollection` não é suportado.
- **Disponibilidade:** os 99,5% e o monitoramento real (RNF-007) são de infraestrutura e não estão neste repositório.
