# teslapark-api

Serviço de gestão de estacionamento. Recebe os eventos publicados pelo sistema de cancelas,
controla a ocupação das vagas e apura a receita por setor.

**Stack:** Kotlin 2.1 · JVM 21 · Micronaut 4 · MySQL 8 · Arquitetura Hexagonal

| Documento | Conteúdo |
|---|---|
| [ADR-0001](docs/adr/0001-arquitetura-teslapark-api.md) | Decisões técnicas e suas justificativas, modelo de dados e requisitos não funcionais |
| [Especificações](docs/specs.md) | Plano de implementação — escopo, regras e critérios de aceite de cada entrega |
| [OpenAPI](docs/api/openapi.yaml) | Contrato da API, versionado. É a fonte de verdade |

---

## Subir o ambiente

```bash
make up
```

Constrói a imagem, sobe MySQL, o emulador do Gate Control System, a API, Prometheus e Grafana,
e aguarda o readiness. Não é preciso criar rede nem volume — o Compose cuida disso.

| Serviço | Endereço |
|---|---|
| API | http://localhost:3003 |
| Swagger UI | http://localhost:3003/swagger/index.html |
| Gate Control System (emulador) | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| MySQL | `localhost:3306` |

O emulador só começa a publicar eventos **depois** que alguém consulta a configuração:

```bash
curl http://localhost:3000/garage
```

Tanto o `make up` quanto o `make dev` sobem a stack em ordem: primeiro o MySQL, que é truncado se já
tiver dados; depois o Gate Control System e a monitoria; e por último a API, que ao subir encontra o
simulador de pé e sincroniza a configuração do estacionamento no `StartupEvent`. Assim cada execução
começa com o estacionamento vazio — o simulador sempre replica a mesma sequência de eventos, e sessões
antigas poluiriam a leitura.

O truncate preserva o schema e o histórico do Flyway, então as migrações não são reexecutadas. As
tabelas vêm do `information_schema`, então migrações novas entram sem editar o script. Use
`SKIP_DB_TRUNCATE=1 make up` para preservar os dados da execução anterior.

Demais alvos: `make down` derruba a stack, `make logs` acompanha, `make reset-db` destrói o volume e
recria o banco do zero, `make smoke` valida o ambiente containerizado e `make test` roda a suíte completa.

### Desenvolvendo na IDE

```bash
make dev
```

Sobe MySQL, simulador, Prometheus e Grafana, deixa a **porta 3003 livre** para a aplicação rodar na
IDE — e o simulador continua entregando os eventos nela. Depois, dispare a simulação com
`curl http://localhost:3000/garage`.

Isso exige um encaminhador: a imagem `garage-sim:1.0.0` resolve o webhook em `localhost:3003`
**dentro do próprio namespace de rede**, ignorando a variável de ambiente que documenta o contrário.
No Docker Desktop, `--network=host` também não resolve — o container entra na rede da VM, não na do
host, e não alcança a porta da IDE. Por isso o `make dev` sobe um `socat` de cinco linhas dentro
daquele namespace, encaminhando `localhost:3003` para `host.docker.internal:3003`. É também a razão
de a API compartilhar o namespace do simulador no modo containerizado, e de a porta dela ser
publicada pelo serviço do simulador.

Para depurar a aplicação **dentro** do container, com breakpoints no IDE:

```bash
make debug
```

Sobe a stack normal com a JVM aberta em `localhost:5005`; no IntelliJ, *Run → Edit Configurations →
Remote JVM Debug*.

---

## Arquitetura

A regra de dependência aponta para dentro: a infraestrutura conhece o domínio, nunca o contrário.

```mermaid
flowchart TB
    subgraph entrada["Adaptadores de entrada"]
        WH["WebhookController<br/>POST /webhook"]
        RV["RevenueController<br/>GET /revenue"]
        AD["GarageAdminController<br/>POST /admin/garage/sync"]
    end

    subgraph app["Application — casos de uso"]
        PE["ProcessGateEvent"]
        HE["HandleEntryEvent · HandleParkedEvent · HandleExitEvent"]
        GR["GetDailyRevenue · ReconcileDailyRevenue"]
        SY["SyncGarageConfiguration"]
    end

    subgraph dom["Domain — Kotlin puro, sem framework"]
        MD["ParkingSession · Sector · Spot · Money · LicensePlate"]
        PO["PricingStrategy · OccupancyStrategy · OperatingHoursStrategy"]
        PT["Portas de saída"]
    end

    subgraph saida["Adaptadores de saída"]
        DB[("MySQL<br/>Micronaut Data JPA")]
        GCS["GateControlSystemClient<br/>GET /garage"]
        MT["MicrometerMetricsPublisher"]
        CK["SystemClockProvider"]
    end

    entrada --> app --> dom
    PT -.implementadas por.-> saida
```

As políticas de negócio são interfaces (`PricingStrategy`, `OccupancyStrategy`,
`OperatingHoursStrategy`) com implementações injetadas — trocar a tabela de preços é registrar outra
implementação, não editar a existente.

**Por que hexagonal:** as regras que dão valor ao sistema — franquia de 30 minutos, hora cheia
arredondada para cima, multiplicador por faixa de lotação, fechamento a 100% — não têm nada a ver
com HTTP, JSON ou SQL, e são justamente as que mais mudam. Isoladas em Kotlin puro, elas rodam em
milissegundos, sem banco e sem subir contexto. Trocar MySQL por outro relacional, ou o emulador por
outra fonte de configuração, é escrever um adaptador novo.

Essa fronteira não é convenção: [`ArchitectureTest`](src/test/kotlin/com/teslapark/architecture/ArchitectureTest.kt)
reprova o build se o domínio importar Micronaut, JPA ou Jackson, se a camada de aplicação importar
persistência ou infraestrutura, se uma porta deixar de ser interface, ou se qualquer arquivo de
produção ganhar um comentário.

---

## Regras de negócio

**Tarifa**

```
duração = exit_time − entry_time
até 30 minutos            → R$ 0,00
acima de 30 minutos       → horas = ceil(duração em minutos ÷ 60)
                            valor = basePrice × horas × multiplicador
```

A primeira hora é cobrada integralmente e o arredondamento é sempre para cima. Dinheiro é
`BigDecimal` com `RoundingMode.CEILING`, nunca ponto flutuante, e a multiplicação é feita numa
única expressão — arredondar a cada passo cobraria centavos a mais.

**Preço dinâmico** — a lotação é medida no instante da entrada e o multiplicador fica congelado na
sessão, então o preço vigente na entrada é o preço cobrado, e o valor é reproduzível e auditável.

As faixas são fechadas no limite superior: o valor do limite pertence à faixa mais barata.

| Lotação | Faixa | Multiplicador |
|---|---|---|
| `≤ 25%` | `LOW` | `0,90` |
| `> 25%` e `≤ 50%` | `NORMAL` | `1,00` |
| `> 50%` e `≤ 75%` | `HIGH` | `1,10` |
| `> 75%` e `< 100%` | `PEAK` | `1,25` |
| `= 100%` | `FULL` | `1,25`, entrada bloqueada |

**Lotação** — a cancela é única, então a decisão de permitir entrada é **global à garagem**, nunca
por setor. Com 100% de ocupação a garagem fecha; a primeira saída reabre.

**Idempotência** — o webhook não garante entrega exatamente-uma-vez. Cada evento tem uma chave
`SHA-256(tipo + placa + discriminador)` com índice único; a duplicata é no-op e responde `200`.

**Dado inconsistente nunca vira `5xx`** — `EXIT` sem `ENTRY`, `PARKED` em coordenada desconhecida e
evento fora de ordem são registrados como anomalia e respondem `200` com `status: IGNORED`. Um `4xx`
faria o produtor entrar em retry infinito de um dado que nunca ficará bom.

---

## Endpoints

O contrato completo está em [`docs/api/openapi.yaml`](docs/api/openapi.yaml) e a própria API o serve
navegável em **http://localhost:3003/swagger/index.html**, com `Try it out` já apontando para ela.

O arquivo continua com **fonte única**: o `processResources` copia `docs/api/openapi.yaml` para dentro
do artefato no build, então não existe uma segunda cópia versionada para sair de sincronia. Os assets
da interface vêm do webjar `org.webjars:swagger-ui`, servidos pela própria aplicação — sem CDN, sem
binário no repositório e funcionando offline.

| Verbo | Rota | Descrição |
|---|---|---|
| `POST` | `/webhook` | Recebe `ENTRY`, `PARKED` e `EXIT` |
| `GET` | `/revenue?date=&sector=` | Receita do dia por setor |
| `POST` | `/revenue` | Mesma consulta, aceitando o corpo JSON `{ "date", "sector" }` |
| `POST` | `/admin/garage/sync` | Ressincroniza a configuração com o Gate Control System |
| `GET` | `/health` · `/health/liveness` · `/health/readiness` | Sondas de saúde |
| `GET` | `/metrics` | Métricas no formato Prometheus |

Erros seguem RFC 7807 em `application/problem+json`, com `type`, `title`, `status`, `detail`,
`instance`, `timestamp`, `requestId` e `errors[]`. Um `500` devolve apenas o `requestId` — nunca
stacktrace, SQL ou nome de tabela.

```bash
curl -X POST http://localhost:3003/webhook -H 'Content-Type: application/json' \
  -d '{"license_plate":"ZUL0001","entry_time":"2026-08-15T12:00:00.000Z","event_type":"ENTRY"}'
```

```bash
curl 'http://localhost:3003/revenue?date=2026-08-15&sector=A'
```

---

## Três decisões que fogem do óbvio

**`GET /revenue` também responde em `POST`.** O cliente legado envia a consulta no corpo de um `GET`.
O servidor Netty do Micronaut não decodifica corpo em `GET` — `HttpMethod.permitsRequestBody` exclui
o verbo, e os bytes são descartados antes de chegar à rota. A forma canônica passou a ser
`GET /revenue?date=&sector=`, e o payload legado é aceito em `POST /revenue`, com resposta idêntica.

**A vaga é marcada como ocupada no `PARKED`, não no `ENTRY`.** O evento de entrada traz apenas a
placa; qual vaga o veículo ocupou só se sabe no `PARKED`, que identifica a vaga por coordenada
geográfica. A decisão de lotação, essa sim, é tomada na entrada — ela conta **sessões abertas**
contra a capacidade total, então a garagem fecha no momento correto mesmo antes de qualquer
`PARKED` chegar.

**A janela de operação do setor não é aplicada.** `SectorOperatingHoursPolicy` existe e é testada, mas
nenhum caso de uso a invoca: os setores publicados pelo emulador trazem limites de permanência
(60 minutos no setor B) que rejeitariam o próprio fluxo de eventos que ele gera. A política fica
disponível como ponto de extensão, com a decisão registrada aqui em vez de escondida no código.

---

## Testes

```bash
./gradlew check
```

Roda compilação, `ktlint`, `detekt`, a suíte unitária, a suíte de integração e os portões de
cobertura. O build quebra se qualquer um reprovar.

| Suíte | Escopo | Tempo |
|---|---|---|
| `test` | Domínio, políticas e casos de uso com fakes em memória — sem I/O | ~1 s |
| `integrationTest` | MySQL 8 real via Testcontainers, contratos HTTP, concorrência e ponta a ponta | ~20 s |

Bancos em memória são proibidos: H2 mente sobre dialeto, locks e tipos decimais — justamente o que
precisa ser validado. O `EndToEndTest` sobe a **imagem real** do emulador e percorre o cenário
completo: encher a garagem, entrada negada, saída, entrada aceita e receita consistente.

| Portão | Mínimo | Atual |
|---|---|---|
| Cobertura do domínio | 90% | 94% |
| Cobertura global | 80% | 94% |

---

## Observabilidade

`GET /metrics` expõe as métricas de negócio — ocupação, vagas ocupadas, receita por setor, entradas
negadas, permanência, faixa tarifária aplicada, eventos do webhook por resultado e anomalias por
tipo — além das técnicas (latência por endpoint com histograma, pool JDBC, JVM, CPU).

Dois dashboards sobem provisionados em http://localhost:3001, sem clique nenhum — nada de importar
JSON na mão. As capturas abaixo são do ambiente rodando contra o simulador.

### Dashboard de infraestrutura

Responde "a API está saudável?". É o painel de plantão, com janela curta e refresh de 10s.

![Dashboard de infraestrutura](docs/imgs/teslapark-api-infraestrutura.png)

| Gráfico | Métrica | Para que serve |
|---|---|---|
| **Requisições por segundo** | `http_server_requests_seconds_count` por `uri` | Volume por endpoint, com `/metrics` e `/health` filtrados para não inflar o número. A série `UNMATCHED_URI` denuncia chamadas a rotas que não existem |
| **Taxa de erro por status** | mesma métrica, `status=~"4..\|5.."` | Separa erro do cliente (4xx) de falha nossa (5xx). Foi essa série que expôs os `400` do simulador |
| **Latência p50 / p95 / p99 do /webhook** | `histogram_quantile` sobre `..._bucket` | A cauda importa mais que a média: o p99 mostra o pior caso que a cancela sente. Exige o histograma habilitado por um `MeterFilter` |
| **Pool de conexões JDBC** | `hikaricp_connections_{active,idle,max}` | Ativas colando no máximo é o primeiro sinal de contenção — as travas `FOR UPDATE` seguram conexão |
| **Memória da JVM (heap)** | `jvm_memory_{used,max}_bytes{area="heap"}` | Usada contra máxima; crescimento que não volta depois do GC indica vazamento |
| **Pausas de GC** | `rate(jvm_gc_pause_seconds_sum[1m])` | Tempo parado no coletor. Explica picos de latência que não vêm do banco |
| **CPU do processo** | `process_cpu_usage` e `system_cpu_usage` | Distingue "a API está trabalhando" de "a máquina está saturada" |
| **Uptime** | `process_uptime_seconds` | Reinício inesperado aparece como queda a zero |
| **Threads da JVM** | `jvm_threads_live_threads` | Contagem estável; escalada contínua indica thread vazando |
| **Eventos do webhook por resultado** | `increase(webhook_events_total[5m])` por `result` | A mesma métrica de negócio vista como saúde de ingestão: `rejected` subindo é problema de contrato |

### Dashboard de negócio

Responde "o estacionamento está funcionando?". Janela de um dia inteiro, para ver o acumulado.

![Dashboard de negócio](docs/imgs/teslapark-api-negocio.png)

| Gráfico | Métrica | Para que serve |
|---|---|---|
| **Taxa de ocupação** | `garage_occupancy_rate` | O gauge é o número que governa o preço e o fechamento. Em 100%, a garagem fecha e as entradas passam a ser negadas |
| **Vagas ocupadas** / **Capacidade total** | `garage_occupied_spots`, `garage_total_capacity` | O numerador e o denominador da ocupação, separados. A capacidade vem do `GET /garage` do simulador, então mudar de zero confirma que o sync no startup funcionou |
| **Receita do dia (acumulada)** | `sum(parking_revenue_total)` | O número que o `/revenue` devolve, em forma de curva. Degraus revelam quando cada sessão foi faturada |
| **Receita acumulada por setor** | `sum by (sector)` | Compara setores com `basePrice` diferente. Um setor plano enquanto o outro sobe costuma significar que ninguém estacionou nele |
| **Faixa de preço aplicada** | `pricing_multiplier_applied_total` por `tier` | Quantas cobranças caíram em `LOW`, `NORMAL`, `HIGH`, `PEAK` e `FULL`. Torna a regra de preço dinâmico auditável: na captura, `PEAK` domina porque a garagem passou o dia perto da lotação |
| **Entradas negadas por lotação** | `parking_entries_denied_total` | Conta quantos carros foram recusados com a garagem cheia — a regra de fechamento em número, não em promessa |
| **Permanência média (min)** | `parking_session_duration_minutes` (soma/contagem e máxima) | Média junto com a máxima. É o gráfico que explica receita zerada: com o simulador, a permanência fica abaixo dos 30 minutos de carência |
| **Anomalias por tipo** | `session_anomalies_total` por `type` | `DUPLICATE_ENTRY`, `EXIT_WITHOUT_ENTRY`, `OUT_OF_ORDER_EVENT` e `PARKED_UNKNOWN_SPOT`. Evento fora de ordem é registrado, nunca descartado em silêncio |
| **Eventos da cancela por tipo** | `webhook_events_total` por `event_type` | `ENTRY`, `PARKED` e `EXIT` devem andar juntos; um `PARKED` muito abaixo dos outros indica câmera ou coordenada divergente |
| **Duplicatas e ignorados** | mesma métrica, `result=~"duplicate\|ignored\|rejected"` | Qualidade da ingestão. `duplicate` é a idempotência trabalhando; `rejected` é payload que não passou na validação |

Logs saem em JSON estruturado quando `LOG_APPENDER=JSON`, com `requestId`, `traceId`, `eventType` e
`sessionId` no MDC. O `X-Request-Id` enviado pelo cliente é propagado; se ausente, é gerado.

---

## Variáveis de ambiente

Copie `.env.example` para `.env` — o `make up` faz isso automaticamente. Nenhum segredo é versionado.

| Variável | Padrão | Para que serve |
|---|---|---|
| `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | `teslapark` | Credenciais do banco |
| `DATASOURCE_URL` | `jdbc:mysql://mysql:3306/teslapark` | Conexão da API |
| `GCS_BASE_URL` | `http://localhost:3000` | Onde a API busca `GET /garage` |
| `SKIP_DB_TRUNCATE` | `0` | Em `1`, o `make up`/`make dev` preserva os dados da execução anterior |
| `API_HOST_PORT` | `3003` | Porta publicada da API |
| `GARAGE_TIMEZONE` | `America/Sao_Paulo` | Fuso que define o dia da receita |
| `GARAGE_CURRENCY` | `BRL` | Moeda |
| `MICRONAUT_ENVIRONMENTS` | `dev` | Perfil (`dev`, `test`, `prod`) |
| `SECURITY_ENABLED` | `false` | Liga o gate de token bearer |
| `RATE_LIMIT_ENABLED` | `false` | Liga o limite de requisições por origem |
| `LOG_APPENDER` | `CONSOLE` | `JSON` para log estruturado |

A API compartilha o namespace de rede do emulador porque a imagem `garage-sim:1.0.0` resolve o
webhook em `localhost:3003` internamente, ignorando a variável de ambiente que documenta o
contrário. Sem isso os eventos não chegariam.
