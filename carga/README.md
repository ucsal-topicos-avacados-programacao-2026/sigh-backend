# Relatório do TST-04: desempenho, carga, criptografia e restauração de backup

Relatório de 20/11/2026. Cobre o RNF-001 (consulta ao prontuário em até 3 s), o RNF-002 (200 usuários simultâneos), o RNF-004 (criptografia, inspecionada pela forma de verificação dele) e a parte de backup do RNF-007.

## Resumo

| Requisito | Resultado | Situação |
|---|---|---|
| RNF-001: consulta ao prontuário em até 3 s | Com 200 usuários, p95 de 23 ms e máximo de 1,15 s. Em estresse (200 usuários sem pausa), p95 de 637 ms e máximo de 1,7 s | Atende, em avaliação parcial (máquina única; veja "Limitações") |
| RNF-001: resumo por IA (medido separado) | Resumo: mediana de 22,9 s; sugestão de risco: mediana de 9,8 s. Com pedidos simultâneos, a maioria passa do limite de 30 s da API | Funciona um pedido por vez; ver achados |
| RNF-002: 200 usuários simultâneos sem falha | 45.897 requisições, 0 falhas, 91.014 verificações de conteúdo certas | Atende, em avaliação parcial (máquina única) |
| RNF-004: transporte, armazenamento, chaves e pontos de decifragem | Só a API e o profissional autorizado veem o texto clínico gravado pela API. Ele não aparece nas tabelas, no WAL, no log nem nas cópias. Resíduo: o texto dos dados de carga, que entrou em claro direto no banco, ficou num segmento de WAL reciclado | Atende para o dado que entra pela API. Ressalvas: o TLS do banco neste ambiente e o resíduo do dado legado (achado 3) |
| RNF-007: restauração com dados fictícios igual à origem | 27 tabelas idênticas à origem (40.002 registros clínicos), restauradas em 15,3 s | Atende. O SLA de 99,5% não é avaliado aqui |

## Ambiente

Tudo numa máquina só. Por isso, os números são de **avaliação parcial**: o RNF-002 fala de uma unidade de médio porte, com rede e servidores reais.

- **Máquina:** Apple M5 (10 núcleos: 4 de desempenho e 6 de eficiência), 16 GB de memória, macOS 26.6.
- **API:** Spring Boot 4.1.2 em Java 21 (OpenJDK 21.0.12), com a configuração do repositório. O pool de conexões com o banco é o padrão do Hikari (10), e a memória da JVM também fica no padrão. HTTPS no próprio back-end (TLS 1.3), com certificado fictício, na porta 8443.
- **Banco:** PostgreSQL 17.12 no container `sigh-db`, dentro da VM do Colima (4 CPUs e 6 GB), com a configuração padrão da imagem (`shared_buffers` de 128 MB).
- **Gerador de carga:** k6 2.4.0, na mesma máquina.
- **IA:** Ollama 0.40.16 com `qwen3:8b`, na mesma máquina e com o modelo inteiro na GPU. O Ollama atende um pedido por vez (`OLLAMA_NUM_PARALLEL=1`).

## Dados fictícios

O arquivo `dados-de-carga.sql` monta uma unidade de médio porte (Hospital A) que consulta prontuários com registros de outra instituição da rede (Hospital B):

- 200 profissionais no Hospital A: 150 médicos, 40 enfermeiros e 10 administrativos. Médicos e enfermeiros usam segundo fator;
- 2.000 pacientes, todos com consentimento amplo;
- 4.000 atendimentos: para cada paciente, um encerrado no Hospital B e um atual no Hospital A. Dos atuais, 300 estão na fila;
- 40.000 registros clínicos, 20 por paciente, metade de cada instituição.

O texto clínico entra em claro e é cifrado pela própria aplicação na subida seguinte: foram 64.190 valores, e a subida levou 38 s. Depois do cenário principal, o banco tinha 115 MB, com 32.269 registros de auditoria, um para cada consulta.

## Método e resultados

O banco de carga foi recriado do zero para a rodada final do cenário principal e para a inspeção da criptografia. O estresse, a medição da IA e o ensaio de restauração usaram o banco anterior, com os mesmos dados e mais os acessos das rodadas anteriores. Por isso o ensaio tem 40.002 registros clínicos e 478 MB, contra 40.001 e 115 MB na inspeção.

### Carga (RNF-001 e RNF-002): `carga.js`

Cada usuário virtual é um profissional diferente. Antes da carga, cada um faz login com senha e com o segundo fator, como no sistema real. Depois, repete sua operação:

| Perfil | Operação |
|---|---|
| Médico | 80% consulta ao prontuário de um paciente sorteado, com os registros do Hospital B; 20% fila |
| Enfermeiro | 50% consulta ao prontuário; 50% fila |
| Administrativo | Busca de paciente por CPF |

Toda resposta é conferida pelo conteúdo, e não só pelo status:
- o prontuário traz os 20 registros, e entre eles os do Hospital B;
- a fila traz os 300 pacientes;
- a busca devolve o paciente certo.

O tempo medido vai do envio do pedido ao fim do recebimento da resposta, pelo k6. O teste sobe para 200 usuários em 1 min, mantém os 200 por 5 min e desce em 30 s.

**Cenário principal:** 200 usuários, com uma pausa sorteada de 0 a 3 s entre as operações (média de 1,5 s).

| Operação | Mediana | p95 | p99 | Máximo |
|---|---|---|---|---|
| Consulta ao prontuário | 17 ms | 23 ms | 42 ms | 1,15 s |
| Fila | 6 ms | 9 ms | 18 ms | 1,11 s |
| Busca por CPF | 3 ms | 4 ms | 7 ms | 1,05 s |
| Login (senha ou segundo fator), feito antes da carga, um usuário após o outro | 111 ms | 117 ms | 126 ms | 391 ms |

O cenário fez 45.897 requisições, a 110 por segundo, e nenhuma falhou. A API usou até 758 MB de memória e o banco, até 91% de CPU.

Esta é a rodada final, com o banco recriado e a API recém-subida. Os máximos ficaram parecidos nas três operações (de 1,05 a 1,15 s). O resumo do k6 não mostra quando eles ocorreram, e a causa não foi identificada. Numa rodada anterior, com a API já aquecida, a consulta teve p95 de 23 ms e máximo de 184 ms.

**Estresse:** os mesmos 200 usuários, sem pausa (2 min de patamar), para achar o limite desta máquina.

| Pool de conexões | Vazão | Prontuário p95 | Prontuário máx. | Falhas | CPU do banco |
|---|---|---|---|---|---|
| 10 (padrão) | 316 req/s | 637 ms | 1,7 s | 0 | até 153% |
| 30 | 394 req/s | 557 ms | 1,5 s | 0 | até 237% |

A vazão é a média da rodada inteira, incluindo a subida de 1 min, a descida de 30 s e o login inicial. No patamar de 200 usuários, ela foi maior.

Sem pausa, as requisições esperam por uma das 10 conexões com o banco, e o tempo sobe junto para todas as operações. Com 30 conexões, a vazão aumenta 25%. Esta comparação não controla tudo:
- a rodada com 30 conexões começou poucos segundos depois de a API subir, ainda sem o aquecimento da JVM, e com o banco já maior pelos acessos anteriores. Isso a desfavorece, então o ganho não é exagerado;
- o banco chegou a cerca de 230% dos 400% possíveis da VM, então ele não ficou saturado. O próximo limite não foi identificado.

Mesmo no estresse, a consulta fica bem abaixo dos 3 s.

### IA (RNF-001, medida separadamente): `medir-ia.sh`

Os pedidos passam pela API, como os do sistema real:
- o resumo do histórico é de um paciente com 20 registros;
- a sugestão de risco vem logo depois de registrar uma triagem.

| Medição | Resultado |
|---|---|
| Primeiro pedido, com o modelo ainda carregando | 29,3 s. Numa rodada anterior, passou dos 30 s e voltou 503 |
| Resumo, 10 pedidos um após o outro | 10 de 10 certos; mínimo de 22,7 s, mediana de 22,9 s, máximo de 26,8 s |
| Sugestão de risco, 10 pedidos um após o outro | 10 de 10 certos; mínimo de 9,1 s, mediana de 9,8 s, máximo de 11,3 s |
| Resumo, 5 pedidos ao mesmo tempo | 1 de 5 certo (28,0 s); os outros 4 voltaram 503 aos 30 s |

Quando a IA não responde a tempo, a API devolve 503 com a mensagem "siga sem a sugestão da IA", e o profissional segue sem ela. Ver os achados.

### Criptografia (RNF-004): `inspecionar-criptografia.sh`

A inspeção segue a forma de verificação do RNF-004: transporte, armazenamento, chaves e pontos de decifragem, cada um separadamente. Um médico grava, pela API, um registro com um marcador único. Depois, o script procura esse marcador.

| Inspeção | Resultado |
|---|---|
| Cliente → API | TLS 1.3 (`TLS_AES_256_GCM_SHA384`) e HSTS. HTTP em claro na mesma porta é recusado com 400, sem chegar à aplicação |
| API → banco | **Sem TLS neste ambiente:** o `sigh-db` é um PostgreSQL comum, na mesma máquina. O perfil `tls` exige `verify-full`, e o `TlsTest` prova isso. O executor de backup do ensaio abaixo conectou com TLS 1.3 |
| Ponto de decifragem | O médico autorizado lê o marcador em claro pela API. O administrativo recebe 403 na mesma consulta |
| Banco | 0 valores fora do formato cifrado. Foram conferidas 9 colunas, entre elas `registro_clinico.conteudo` (40.001 valores), sinais vitais e `usuario.mfa_segredo`. O valor gravado começa com `sigh:v2:1:` |
| Arquivos do banco: marcador gravado pela API | 0 arquivos nas tabelas e índices (`base/`) e 0 no WAL (`pg_wal/`). Controle: a categoria do mesmo registro, que não é dado clínico e foi gravada junto, é achada em `base/` e em `pg_wal/` |
| Arquivos do banco: texto dos dados de carga | 0 arquivos em `base/`, porque a cifragem ao subir regrava as tabelas com `VACUUM FULL`. Em `pg_wal/`, achado em 1 segmento (achado 3) |
| Log da API | O marcador aparece em 0 linhas |
| Chave dos dados | Não aparece na linha de comando da API, no ambiente do container do banco, nos arquivos do banco nem em todo o histórico do git. Ela fica no ambiente da API, que é quem decifra. O arquivo de ambiente fica fora do repositório, com permissão `rw-------` |

O serviço de IA, o outro ponto de decifragem, só recebe dado na própria máquina ou num host autorizado (BE-23, `IAConfigTest`).

### Restauração de backup (RNF-004 e RNF-007): `ensaiar-restauracao.sh`

O ensaio usa a mesma imagem e os mesmos scripts de produção (`docker/backup` e `backup/`), com os dados de carga já cifrados:

1. Sobe um banco com TLS e arquivamento de WAL cifrado, e carrega nele os dados.
2. Faz um backup completo, altera dados e faz um incremental.
3. Altera de novo. Essa última mudança só volta pelo WAL arquivado.
4. Restaura a partir da cópia externa e compara todas as tabelas com a origem: número de linhas e md5 de todas as linhas.

| Etapa | Resultado |
|---|---|
| Executor → banco | TLS 1.3 com o certificado conferido (`verify-full`) |
| Backup completo | 22,8 s; 191 MB cifrados, com o banco em 478 MB |
| Backup incremental | 10,0 s |
| Restauração: decifrar, combinar, verificar, aplicar o WAL e subir | 15,3 s |
| Comparação com a origem | **Iguais**, nas 27 tabelas: 40.002 registros clínicos, 4.000 atendimentos e 2.000 pacientes. A instituição criada depois do último backup voltou pelo WAL |
| Texto clínico em claro | Em 0 arquivos restaurados, 0 cópias e 0 segmentos de WAL arquivados. Controle: o nome de um paciente é achado nos arquivos restaurados |

A suíte automatizada também restaura com dados fictícios e compara com a origem (`BackupERestauracaoTest`).

## Limitações

- **Uma máquina só:** API, banco, gerador de carga e IA dividem o mesmo processador. Não há rede de verdade entre o cliente e a API. Os números valem como avaliação parcial do RNF-002 e não representam uma unidade hospitalar real.
- **Banco sem TLS nas medições de carga:** a consulta foi medida sem o TLS entre a API e o banco. O custo desse TLS não entrou nos números, mas o transporte entre a API e o cliente foi medido com TLS.
- **Perfil de uso estimado:** a mistura de operações e a pausa entre elas são uma estimativa, não uma medição de uso real. Cada consulta ao prontuário grava um registro de auditoria, mas as escritas clínicas (registro de evolução, triagem) não entraram na carga, só na inspeção e na medição da IA.
- **CPU da API:** foi medida com o `ps` do macOS, que mostra uma média móvel. Os valores são aproximados.
- **IA local:** os tempos dependem da GPU desta máquina. Em outra máquina ou com outro modelo, mudam.
- **Resíduo no WAL varia:** numa inspeção feita logo depois da cifragem, também com o banco recriado, o texto dos dados de carga não apareceu em `pg_wal/`. Depois do cenário principal, apareceu num segmento reciclado. Isso depende de quais segmentos o PostgreSQL reaproveita.
- **Disponibilidade:** o SLA de 99,5% (RNF-007) depende de monitoramento real ao longo de um mês e não é avaliado aqui.

## Achados

1. **IA com tempo-limite apertado.** O resumo leva cerca de 23 s e o limite da API é de 30 s (`sigh.ia.tempo-limite`):
   - com o modelo ainda carregando (a primeira vez, ou depois de 5 min parado, o padrão do Ollama), o pedido fica no limite e pode voltar 503;
   - com pedidos simultâneos, eles fazem fila no Ollama e a maioria passa do limite.

   Para a apresentação:
   - faça um resumo antes de começar, para carregar o modelo;
   - suba o Ollama com `OLLAMA_KEEP_ALIVE=-1`;
   - não peça resumos em paralelo.

   Para o sistema: rever o limite e a forma de gerar o resumo (card BE-25).
2. **Pool de conexões.** No estresse, a vazão média foi de 316 req/s com 10 conexões e de 394 req/s com 30. Para a carga do RNF-002 o padrão sobra (110 req/s), então nada foi mudado.
3. **Dado legado em segmento de WAL reciclado.** O texto dos dados de carga, que entrou em claro direto no banco antes da cifragem, nesta ou numa carga anterior no mesmo servidor (o WAL é do servidor inteiro), foi achado no segmento `00000001000000000000009A`, com o texto de milhares de registros (o grep conta 12.572 linhas com "do paciente"). Esse segmento estava à frente da posição atual do WAL (`…85`): é um arquivo antigo que o PostgreSQL renomeia para reaproveitar, e o conteúdo só some quando ele é sobrescrito. O dado que entra pela API não deixa esse resíduo, porque já chega ao banco cifrado. O README já registra o limite ("WAL de antes da cifragem"); este teste mostra que ele vale também para segmentos reciclados no servidor do banco. Para o sistema: depois da migração, forçar a troca de segmentos até sobrescrever os antigos ou apagar os reciclados com o banco parado, e documentar isso no procedimento (card BE-26).

## Como reproduzir

Pré-requisitos:
- o banco local (`sigh-db`) e o k6 (`brew install k6`);
- para a IA, o Ollama com `qwen3:8b`;
- no arquivo de ambiente (`~/.config/sigh/sigh-local.env`, fora do repositório), além das variáveis da aplicação:
  - `SIGH_SENHA_CARGA`: a senha dos profissionais de teste (`openssl rand -hex 16`);
  - `SIGH_MFA_CARGA`: o segredo do segundo fator deles, em Base32.

```bash
# Certificado fictício da API
mkdir -p /tmp/sigh-cert && openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 30 \
  -subj /CN=localhost -addext subjectAltName=DNS:localhost,IP:127.0.0.1 \
  -keyout /tmp/sigh-cert/api-chave.pem -out /tmp/sigh-cert/api.pem
./mvnw -DskipTests package
docker exec sigh-db psql -U postgres -c 'create database sigh_carga'
carga/subir-api.sh /tmp/sigh-cert     # cria o esquema; pare a API depois que subir

set -a; . ~/.config/sigh/sigh-local.env; set +a   # o k6 lê SIGH_SENHA_CARGA e SIGH_MFA_CARGA do ambiente
carga/carregar-dados.sh               # senha e segredo pela entrada do psql, não como argumento
carga/subir-api.sh /tmp/sigh-cert > /tmp/sigh-api.log 2>&1 &   # cifra os dados ao subir

k6 run carga/carga.js                          # cenário principal
k6 run -e PAUSA=0 -e DURACAO=2m carga/carga.js # estresse
# estresse com 30 conexões: suba a API com carga/subir-api.sh /tmp/sigh-cert --spring.datasource.hikari.maximum-pool-size=30
carga/medir-ia.sh
carga/inspecionar-criptografia.sh /tmp/sigh-api.log
carga/ensaiar-restauracao.sh
```

A inspeção grava um registro no paciente 1, o que muda a contagem de registros conferida pela carga. Rode a carga antes dela, ou recrie o banco de carga.
