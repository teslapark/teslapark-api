# Especificações de Implementação — teslapark-api

Este documento é o plano de execução do serviço. Cada **SPEC** abaixo corresponde a **exatamente um commit**: escopo fechado, testes próprios e build verde ao final. Nenhuma SPEC depende de código que ainda não foi entregue por uma SPEC anterior.

Decisões de arquitetura e justificativas: `docs/adr/0001-arquitetura-teslapark-api.md`.

---

## Princípios inegociáveis

| # | Regra | Detalhe |
|---|---|---|
| 1 | **Código sem comentários** | Nenhum comentário em código de produção. Se um trecho precisa de explicação, ele precisa de um nome melhor ou de ser extraído para uma função nomeada. KDoc apenas em interfaces públicas de porta, quando o contrato não for óbvio pela assinatura. |
| 2 | **Tudo em inglês** | Pacotes, classes, funções, variáveis, tabelas, colunas, mensagens de erro, logs, mensagens de commit e nomes de teste. Apenas este documento e o ADR ficam em português. |
| 3 | **Nomes revelam intenção** | `calculateChargeableHours()` em vez de `calc()`. `isWithinFreeWindow` em vez de `flag`. Booleanos como afirmação. Sem abreviação que não seja do domínio. |
| 4 | **Domínio puro** | O módulo `domain` não importa Micronaut, JPA, Jackson ou qualquer framework. Isso é garantido pela topologia Gradle e verificado por teste. |
| 5 | **Testes no mesmo commit** | Toda SPEC entrega implementação e testes juntos. Um commit que não passa no `./gradlew check` não existe. |
| 6 | **Sem números mágicos** | `FREE_WINDOW_MINUTES`, `OccupancyTier.LOW.multiplier`. Constantes nomeadas no domínio, nunca literais espalhados. |
| 7 | **Dinheiro é `BigDecimal`** | Nunca `Double` ou `Float`. `DECIMAL` no banco, `RoundingMode.CEILING` no arredondamento tarifário. |
| 8 | **Tempo é injetado** | `Clock` é porta. Nenhum `Instant.now()` fora do adaptador `SystemClock`. |

---

## Convenções

### Estrutura de módulos Gradle

A regra de dependência da arquitetura hexagonal é **estrutural**, não convencional — o compilador impede a violação.

```
teslapark-api/
├── domain/          Kotlin puro. Zero dependências de framework.
├── application/     Casos de uso. Depende de :domain.
├── infrastructure/  Adaptadores (HTTP, JPA, GCS client). Depende de :application e :domain.
├── bootstrap/       Application entrypoint, configuração, wiring. Depende de todos.
├── docs/adr/        Architecture Decision Records.
└── docker/          Compose e assets de infraestrutura local.
```

### Pacote raiz

`com.teslapark`

### Glossário de domínio (usar exatamente estes termos no código)

| Termo | Significado |
|---|---|
| `Garage` | A instalação física completa, com uma única cancela de entrada |
| `Sector` | Divisão lógica e comercial do pool de vagas; define tarifa e janela de operação |
| `Spot` | Vaga individual, identificada por coordenada geográfica |
| `ParkingSession` | Ciclo de vida de um veículo: entrada → estacionado → saída |
| `GateControlSystem` (GCS) | Sistema externo que controla as cancelas e publica os eventos |
| `OccupancyTier` | Faixa de lotação que determina o multiplicador tarifário |
| `FreeWindow` | Franquia inicial de 30 minutos sem cobrança |
| `ChargeableHours` | Horas cheias faturáveis, arredondadas para cima |

### Mensagens de commit — Conventional Commits

```
<type>(<scope>): <descrição imperativa em inglês, minúscula, sem ponto final>

<corpo opcional: o porquê, nunca o quê>
```

Tipos: `feat`, `fix`, `chore`, `test`, `docs`, `refactor`, `perf`.

### Definition of Done (aplicável a toda SPEC)

- [ ] `./gradlew check` verde (compilação, `ktlint`, `detekt`, testes)
- [ ] Zero comentários em código de produção
- [ ] Zero identificador em português
- [ ] Testes cobrem o caminho feliz **e** as bordas descritas na SPEC
- [ ] Nenhum arquivo fora do escopo declarado da SPEC foi tocado

---

## SPEC-01 — Bootstrap do projeto

**Objetivo:** esqueleto compilável com os quatro módulos e os portões de qualidade ativos desde o primeiro commit.

**Entregáveis**

- `settings.gradle.kts` com os módulos `domain`, `application`, `infrastructure`, `bootstrap`
- `gradle/libs.versions.toml` (version catalog): Kotlin 2.1.x, Micronaut 4.x, KSP, JUnit 5, Kotest assertions, MockK, Testcontainers, Flyway, Micrometer
- `build.gradle.kts` raiz com `ktlint`, `detekt`, JaCoCo e toolchain JVM 21
- Módulo `domain` **sem nenhuma dependência** além do stdlib Kotlin
- `bootstrap`: `Application.kt` com `main`, `application.yml` base
- `.gitignore`, `.editorconfig`, `gradle/wrapper`

**Regras**

- `domain/build.gradle.kts` não declara nenhuma dependência de framework — este arquivo é a primeira linha de defesa da arquitetura
- `detekt` configurado para falhar o build em qualquer violação
- Perfis de configuração: `dev`, `test`, `prod`

**Testes**

- `ApplicationStartupTest`: o contexto sobe e o endpoint `GET /health` responde `200`

**Aceite:** `./gradlew check` passa; `./gradlew run` sobe a aplicação; `/health` responde.

**Commit:** `chore: bootstrap multi-module kotlin micronaut project`

---

## SPEC-02 — Infraestrutura local com Docker Compose

**Objetivo:** qualquer pessoa clona o repositório e sobe o ambiente completo com um comando.

**Entregáveis**

- `docker-compose.yml` com os serviços:
  - `mysql` — MySQL 8.x, healthcheck, volume nomeado, `utf8mb4`
  - `gate-control-system` — emulador do GCS em modo host, para desenvolvimento e testes
  - `api` — a aplicação, construída a partir do `Dockerfile`, dependente do healthcheck do MySQL
- `Dockerfile` multi-stage (build Gradle → runtime JRE 21 slim, usuário não-root)
- `.env.example` com todas as variáveis; `.env` no `.gitignore`
- `Makefile` com alvos `up`, `down`, `logs`, `test`, `reset-db`

**Regras**

- Nenhum segredo versionado; toda credencial vem de variável de ambiente
- O compose sobe em rede isolada, exceto o GCS, que exige modo host
- Healthchecks reais em todos os serviços — `depends_on` sem healthcheck não garante nada

**Testes**

- Script de smoke `docker/smoke.sh`: sobe o compose, aguarda readiness, verifica `GET /health/readiness`

**Aceite:** `make up` sobe MySQL + GCS + API; `make down` limpa tudo.

**Nota de escopo:** o provisionamento em Azure (IaC, monitoria gerenciada, pipeline) será tratado em **repositório separado** (`teslapark-infra`), com Terraform. Este repositório mantém apenas a infraestrutura local.

**Commit:** `chore: add local infrastructure with docker compose`

---

## SPEC-03 — Schema de banco de dados

**Objetivo:** schema completo, versionado e reprodutível do zero.

**Entregáveis**

- `infrastructure/src/main/resources/db/migration/V1__create_garage_schema.sql`
- Tabelas: `garage`, `garage_state`, `sector`, `parking_spot`, `vehicle`, `parking_session`, `sector_daily_revenue`, `webhook_event`, `session_anomaly`
- Integração Flyway no boot (`flyway.enabled=true`)

**Regras**

| Item | Definição |
|---|---|
| Valores monetários | `DECIMAL(10,2)`; `sector_daily_revenue.total_amount` em `DECIMAL(14,2)` |
| Coordenadas | `DECIMAL(9,6)` com `UNIQUE (lat, lng)` em `parking_spot` |
| Timestamps | `TIMESTAMP(3)` em UTC |
| Sessão ativa única por placa | coluna gerada `active_plate` (= placa quando `status <> 'EXITED'`, senão `NULL`) + `UNIQUE (active_plate)` |
| Idempotência | `webhook_event.idempotency_key CHAR(64)` com `UNIQUE` |
| Receita diária | `UNIQUE (sector_id, revenue_date)` |
| Chaves estrangeiras | InnoDB, `ON DELETE RESTRICT` |
| Índices | `(sector_id, revenue_date)`, `(status, entry_time)`, `(sector_id, occupied)`, `(received_at)` |

**Testes**

- `SchemaMigrationTest` (Testcontainers, MySQL 8 real): migração aplica do zero sem erro
- Índice único de `active_plate` rejeita a segunda sessão aberta para a mesma placa
- `UNIQUE (idempotency_key)` rejeita duplicata

**Aceite:** migração roda em banco vazio e é idempotente em reexecução.

**Commit:** `feat: add database schema migrations`

---

## SPEC-04 — Modelo de domínio

**Objetivo:** o núcleo do negócio, em Kotlin puro, sem framework.

**Entregáveis** (módulo `domain`)

```
domain/model/
  LicensePlate.kt        value class, normaliza para maiúsculas, valida não-vazio
  Money.kt               wrapper de BigDecimal com escala 2 e moeda
  Coordinates.kt         lat/lng normalizados em BigDecimal(9,6)
  SectorCode.kt          value class
  Sector.kt              basePrice, maxCapacity, openHour, closeHour, durationLimit
  Spot.kt                externalId, sectorCode, coordinates, occupied
  ParkingSession.kt      máquina de estados imutável
  SessionStatus.kt       ENTERED, PARKED, EXITED, CANCELLED
  Garage.kt              agregado de setores, capacidade total
  Occupancy.kt           occupiedSpots, totalCapacity, rate
domain/event/
  GateEvent.kt           sealed interface: EntryEvent, ParkedEvent, ExitEvent
domain/error/
  DomainError.kt         sealed hierarchy
```

**Regras da máquina de estados**

| Transição | Resultado |
|---|---|
| `ENTERED → PARKED` | Válida; atribui `spot` e `parkedAt` |
| `ENTERED → EXITED` | Válida (saída sem estacionar registrado); fatura normalmente |
| `PARKED → EXITED` | Válida; libera a vaga |
| `EXITED → *` | Inválida; retorna `SessionAlreadyClosed` |
| Qualquer retrocesso | Inválido; estado só avança |

- `ParkingSession` é imutável: cada transição retorna nova instância via `copy()`
- Erros de domínio são **tipos retornados**, não exceções de controle de fluxo
- `LicensePlate` normaliza no construtor; comparação nunca é case-sensitive por acidente

**Testes**

- Cada transição válida e inválida da tabela acima
- `LicensePlate("zul0001") == LicensePlate("ZUL0001")`
- `Money` não perde precisão em soma e multiplicação encadeadas
- `Coordinates` iguais com representações distintas (`-23.561684` vs `-23.5616840`) são equivalentes

**Aceite:** módulo `domain` compila sem nenhuma dependência externa; testes rodam em menos de 2 segundos.

**Commit:** `feat: add parking domain model`

---

## SPEC-05 — Políticas de tarifação, lotação e operação

**Objetivo:** todas as regras de negócio monetárias e de acesso, isoladas e exaustivamente testadas.

**Entregáveis** (módulo `domain`)

```
domain/policy/
  PricingPolicy.kt          cálculo do valor da sessão
  OccupancyTier.kt          enum: LOW(0.90), NORMAL(1.00), HIGH(1.10), PEAK(1.25), FULL
  OccupancyPolicy.kt        decide se a garagem aceita entrada
  OperatingHoursPolicy.kt   janela do setor e limite de permanência
```

**Regra de tarifação**

```
duration = exitTime - entryTime
se duration <= 30 minutos          → amount = 0
senão                              → chargeableHours = ceil(duration em minutos / 60)
                                     amount = basePrice × chargeableHours × multiplier
```

**Regra de preço dinâmico** — o multiplicador é determinado pela lotação **no instante da entrada** e congelado na sessão.

| Lotação | Tier | Multiplicador |
|---|---|---|
| `<= 25%` | `LOW` | `0.90` |
| `> 25%` e `<= 50%` | `NORMAL` | `1.00` |
| `> 50%` e `<= 75%` | `HIGH` | `1.10` |
| `> 75%` e `< 100%` | `PEAK` | `1.25` |
| `= 100%` | `FULL` | `1.25`, entrada bloqueada |

**Regra de lotação:** com 100% de ocupação a garagem é fechada para novas entradas; a primeira saída reabre. A avaliação é **global** (cancela única), nunca por setor.

**Testes** (parametrizados — a tabela é a especificação executável)

| Caso | Entrada | Esperado |
|---|---|---|
| Dentro da franquia | 29 min | `0.00` |
| Borda da franquia | 30 min exatos | `0.00` |
| Primeiro minuto cobrado | 31 min, base `40.50` | `40.50` |
| Hora exata | 60 min | `40.50` |
| Arredondamento para cima | 61 min | `81.00` |
| Permanência longa | 130 min | `121.50` |
| Precisão decimal | base `4.10`, 3h, `1.10` | `13.53` |

Bordas de tier obrigatórias — as faixas são fechadas no limite superior, então cada limite exige os dois lados: `0%`, `24.99%`, `25.00%`, `25.01%`, `49.99%`, `50.00%`, `50.01%`, `74.99%`, `75.00%`, `75.01%`, `99.99%`, `100%`.

**Aceite:** cobertura do pacote `domain/policy` acima de 95%; nenhum literal numérico solto na implementação.

**Commit:** `feat: add pricing occupancy and operating hours policies`

---

## SPEC-06 — Portas de saída

**Objetivo:** declarar os contratos que a infraestrutura deverá implementar, invertendo a dependência.

**Entregáveis** (módulo `domain`, pacote `domain/port`)

| Porta | Responsabilidade |
|---|---|
| `SectorRepository` | Leitura e persistência de setores |
| `SpotRepository` | Consulta por coordenada, alocação de vaga com lock, liberação |
| `ParkingSessionRepository` | Sessão ativa por placa, persistência, contagem de ocupação |
| `RevenueRepository` | Acúmulo e consulta do snapshot diário por setor |
| `GarageConfigurationProvider` | Obtenção da configuração da garagem na fonte externa |
| `WebhookEventRepository` | Registro do evento bruto e verificação de idempotência |
| `AnomalyRepository` | Registro de eventos inconsistentes |
| `ClockProvider` | Instante atual e fuso de operação |

**Regras**

- Portas expressam **linguagem de domínio**, nunca vocabulário de persistência: `findActiveSessionFor(plate)`, não `selectByPlateAndStatus`
- Nenhuma porta expõe tipo de framework em assinatura
- Retornos usam tipos de domínio e nulos explícitos, nunca `Optional` encapsulando erro

**Testes**

- Fakes em memória de todas as portas em `domain/src/test`, reutilizados pelos testes de caso de uso

**Aceite:** módulo `application` consegue ser escrito inteiramente contra estas interfaces.

**Commit:** `feat: define domain output ports`

---

## SPEC-07 — Adaptadores de persistência

**Objetivo:** implementar todas as portas de repositório sobre MySQL, com garantia transacional real.

**Entregáveis** (módulo `infrastructure`)

```
infrastructure/persistence/
  entity/       SectorEntity, SpotEntity, VehicleEntity, ParkingSessionEntity,
                SectorDailyRevenueEntity, WebhookEventEntity, SessionAnomalyEntity
  mapper/       funções de extensão toDomain() / toEntity()
  repository/   implementações das portas da SPEC-06
```

**Regras**

- Entidades são **classes normais**, nunca `data class` — evita `equals`/`hashCode` sobre entidade gerenciada
- Alocação de vaga usa `SELECT ... FOR UPDATE SKIP LOCKED`, em transação curta
- A ocupação é contada por consulta indexada, não por contador global em memória
- Acúmulo de receita usa `INSERT ... ON DUPLICATE KEY UPDATE` na mesma transação da saída
- Violação de constraint única é traduzida em erro de domínio, nunca propagada como exceção de driver

**Testes** (Testcontainers com MySQL 8 real — **H2 é proibido**)

- Round-trip de `DECIMAL(10,2)` ↔ `Money` sem perda
- Duas threads concorrentes recebem vagas distintas sob `SKIP LOCKED`
- 30 requisições concorrentes com 1 vaga livre → exatamente 1 aloca
- Rollback não deixa receita órfã nem vaga presa
- `ON DUPLICATE KEY UPDATE` acumula corretamente sob concorrência

**Aceite:** todas as portas de repositório implementadas; suíte de integração isolada em source set próprio.

**Commit:** `feat: add mysql persistence adapters`

---

## SPEC-08 — Cliente do Gate Control System

**Objetivo:** adaptador de saída que obtém a configuração da garagem no sistema externo.

**Entregáveis** (módulo `infrastructure`)

- `GateControlSystemClient` — HTTP declarativo do Micronaut para `GET /garage`
- DTOs de resposta com mapeamento tolerante: aceita `basePrice` **e** `base_price`
- `GateControlSystemConfigurationProvider` — implementação de `GarageConfigurationProvider`
- Retry com backoff exponencial e circuit breaker
- Configuração de URL e timeouts em `application.yml`

**Regras**

- O DTO externo **nunca** vaza para `domain` ou `application`; a conversão acontece no adaptador
- Timeout de conexão e de leitura explícitos — nenhum valor padrão implícito
- Falha do cliente é traduzida em erro de domínio `GarageConfigurationUnavailable`

**Testes**

- Servidor HTTP embarcado devolvendo o payload real (2 setores, 30 vagas) → mapeamento correto
- Payload em camelCase e em snake_case produzem o mesmo resultado
- Timeout e `500` do GCS acionam retry e depois falham com o erro de domínio correto

**Aceite:** a configuração da garagem é obtida e convertida em objetos de domínio sem que `domain` conheça HTTP.

**Commit:** `feat: add gate control system client`

---

## SPEC-09 — Sincronização da configuração da garagem

**Objetivo:** carregar a garagem no boot sem travar a aplicação se a fonte estiver indisponível.

**Entregáveis**

- `SyncGarageConfiguration` (caso de uso, módulo `application`)
- `StartupSynchronizationListener` (`infrastructure`) — dispara no evento de startup
- `GarageConfigurationStatus`: `PENDING`, `SYNCED`, `STALE`
- `POST /admin/garage/sync` para ressincronização manual
- Readiness probe reprovando enquanto o status for `PENDING`

**Regras**

- Sincronização é **idempotente**: reexecutar não duplica setores nem vagas
- Falha no boot **não** derruba a aplicação — apenas mantém readiness reprovado com retry em background
- Endpoints de negócio respondem `503` com `Retry-After` enquanto a configuração não estiver disponível

**Testes**

- Sync popula setores, vagas e capacidade total corretamente
- Sync executado duas vezes não duplica registros
- GCS indisponível: aplicação sobe, readiness falha, retry ocorre, sync conclui quando o GCS volta
- `GET /revenue` responde `503` com status `PENDING`

**Aceite:** aplicação sobe com o GCS fora do ar e se recupera sozinha.

**Commit:** `feat: add garage configuration synchronization`

---

## SPEC-10 — Ingestão de eventos do webhook

**Objetivo:** o coração do sistema — receber, deduplicar e processar `ENTRY`, `PARKED` e `EXIT`.

**Entregáveis**

- `WebhookController` — `POST /webhook`, DTO polimórfico discriminado por `event_type`
- Casos de uso: `HandleEntryEvent`, `HandleParkedEvent`, `HandleExitEvent`
- `EventIdempotencyGuard` — chave `SHA-256(event_type + license_plate + event_time)`
- Persistência do evento bruto **antes** do processamento
- Registro de anomalias: `EXIT_WITHOUT_ENTRY`, `DUPLICATE_ENTRY`, `PARKED_UNKNOWN_SPOT`, `OUT_OF_ORDER_EVENT`

**Regras de processamento**

| Evento | Comportamento |
|---|---|
| `ENTRY` | Bloqueia se lotação = 100% (`409`); congela `basePrice`, `occupancyRate` e `multiplier`; cria sessão em `ENTERED` |
| `PARKED` | Localiza a vaga por coordenada; marca ocupada; transita para `PARKED` |
| `EXIT` | Calcula duração e valor; libera a vaga; acumula receita do dia; transita para `EXITED` |
| Duplicata | `200` com `status: DUPLICATE`, sem alteração de estado |
| Anomalia | `200` com `status: IGNORED`, anomalia persistida, sem receita |

- **Nunca** retornar `5xx` por dado inconsistente da origem — isso provocaria retry infinito
- Toda mutação de estado ocorre em transação única

**Testes**

- Fluxo completo `ENTRY → PARKED → EXIT` com valor correto
- `ENTRY → EXIT` sem `PARKED` fatura normalmente
- `EXIT` sem `ENTRY` → anomalia, `200`, receita inalterada
- Mesmo `ENTRY` 20× em paralelo → 1 sessão, 19 `DUPLICATE`
- `EXIT` duplicado → receita creditada exatamente uma vez
- Garagem lotada → `409`; após um `EXIT`, a próxima entrada é aceita
- `PARKED` com coordenada desconhecida → anomalia, sessão permanece em `ENTERED`
- Evento fora de ordem não retrocede o estado

**Aceite:** todos os cenários de concorrência passam; nenhum over-booking; nenhuma cobrança dupla.

**Commit:** `feat: add webhook event ingestion`

---

## SPEC-11 — API de faturamento

**Objetivo:** expor a receita do dia por setor com leitura de custo constante.

**Entregáveis**

- `RevenueController` — `GET /revenue`, aceitando query params **e** corpo JSON
- `GetDailyRevenue` (caso de uso)
- Atualização do snapshot `sector_daily_revenue` na transação de saída (SPEC-10)
- Job de reconciliação comparando o snapshot com a soma das sessões

**Regras**

- `revenue_date` é a data **local** da saída (`America/Sao_Paulo`), não UTC
- Sem `sector` informado, retorna o total e a quebra por setor
- Setor inexistente → `404`; data inválida → `400`
- `free_sessions_count` é exposto separadamente do total faturado

**Testes**

- Receita reflete exatamente as saídas do dia
- Sessão dentro da franquia incrementa `free_sessions_count` e não o valor
- Saída às 21:00 horário local não vaza para o dia seguinte em UTC
- Consulta de setor inexistente retorna `404`
- Reconciliação: `SUM(amount_charged)` = `total_amount` após 1.000 sessões concorrentes

**Aceite:** `GET /revenue` responde nas duas formas de request com o mesmo resultado.

**Commit:** `feat: add revenue query api`

---

## SPEC-12 — Tratamento de erros

**Objetivo:** contrato de erro único, previsível e que não vaza detalhe interno.

**Entregáveis**

- `GlobalErrorHandler` — mapeamento de `DomainError` → status HTTP
- `ProblemDetail` conforme RFC 7807 (`application/problem+json`)
- `RequestIdFilter` — aceita ou gera `X-Request-Id` e propaga na resposta
- Validação de payload com mensagens de campo

**Catálogo**

| Status | Origem |
|---|---|
| `400` | JSON malformado, campo obrigatório ausente, formato de data inválido |
| `401` | Token ausente ou inválido |
| `403` | Token válido sem o escopo necessário |
| `404` | Setor, placa ou vaga inexistente |
| `409` | Garagem lotada, sessão já aberta, transição inválida |
| `422` | `exit_time` anterior a `entry_time` |
| `429` | Rate limit excedido |
| `500` | Falha não prevista |
| `503` | Configuração não sincronizada ou banco indisponível |

**Regras**

- `500` **nunca** expõe stacktrace, SQL ou nome de tabela; devolve apenas `requestId`
- `503` sempre acompanha `Retry-After`
- Erro de negócio é `WARN` com métrica; erro inesperado é `ERROR`

**Testes**

- Um teste por status do catálogo
- Asserção explícita de que o corpo do `500` não contém `Exception`, `SQL` nem nome de tabela
- `X-Request-Id` enviado pelo cliente é devolvido inalterado

**Aceite:** todo endpoint responde no mesmo formato de erro.

**Commit:** `feat: add rfc7807 error handling`

---

## SPEC-13 — Observabilidade

**Objetivo:** o serviço precisa ser diagnosticável sem acesso ao banco.

**Entregáveis**

- Micrometer + endpoint Prometheus em `GET /metrics`
- Logs estruturados em JSON com `traceId`, `requestId`, `eventType`, `sessionId`
- `GET /health` e `GET /health/readiness` (readiness valida banco **e** status de sincronização)

**Métricas de negócio obrigatórias**

| Métrica | Tipo |
|---|---|
| `garage_occupancy_rate` | Gauge |
| `garage_occupied_spots` | Gauge |
| `parking_entries_denied_total` | Counter |
| `parking_revenue_total{sector}` | Counter |
| `parking_session_duration_minutes` | Histogram |
| `pricing_multiplier_applied_total{tier}` | Counter |
| `webhook_events_total{event_type,result}` | Counter |
| `session_anomalies_total{type}` | Counter |

**Regras**

- Placa **mascarada** em log (`ZUL****`); placa completa apenas em `DEBUG`, desligado em `prod`
- Payload cru nunca é logado — ele já está persistido em `webhook_event.raw_payload`
- Nenhuma métrica de negócio é calculada em varredura completa de tabela

**Testes**

- Após um ciclo completo de eventos, todas as métricas listadas existem com valores coerentes
- Log de evento não contém a placa completa no perfil `prod`
- Readiness reprova com banco indisponível

**Aceite:** `GET /metrics` expõe todas as métricas da tabela.

**Commit:** `feat: add metrics and structured logging`

---

## SPEC-14 — Testes de arquitetura e ponta-a-ponta

**Objetivo:** transformar as regras arquiteturais em asserções executáveis e validar o fluxo completo.

**Entregáveis**

- `ArchitectureTest` (Konsist ou ArchUnit):
  - nenhuma classe em `domain` importa `io.micronaut`, `jakarta.persistence` ou `com.fasterxml`
  - nenhuma classe em `application` importa `jakarta.persistence`
  - toda porta em `domain/port` é interface
  - nenhum arquivo de produção contém comentário de linha ou de bloco
- `EndToEndTest`: compose com MySQL + GCS, sincronização, ciclo completo de eventos, verificação de `GET /revenue`
- Relatório JaCoCo agregado com portões: `domain` ≥ 90%, global ≥ 80%

**Testes**

- Violação proposital da regra de dependência faz o teste de arquitetura falhar
- Cenário E2E: encher a garagem → entrada negada → saída → entrada aceita → receita consistente

**Aceite:** `./gradlew check` cobre unidade, integração, arquitetura e cobertura.

**Commit:** `test: add architecture and end-to-end test suite`

---

## SPEC-15 — Documentação do repositório

**Objetivo:** alguém que nunca viu o projeto sobe, entende e opera em menos de dez minutos.

**Entregáveis**

- `README.md`: visão do serviço, arquitetura em uma imagem, como subir com `make up`, como rodar testes, variáveis de ambiente, endpoints
- `docs/api/openapi.yaml` — contrato completo, versionado
- Link para `docs/adr/0001-arquitetura-teslapark-api.md`

**Regras**

- O README explica **decisões**, não sintaxe de Gradle
- Nenhuma seção descreve o projeto como exercício, demonstração ou prova de conceito
- O OpenAPI é a fonte de verdade do contrato e é validado no CI contra os testes de API

**Aceite:** clone limpo → `make up` → primeiro request bem-sucedido seguindo apenas o README.

**Commit:** `docs: add readme and openapi specification`

---

## Ordem de execução e dependências

```
01 bootstrap
 └─ 02 docker compose
     └─ 03 schema
         └─ 04 domain model
             └─ 05 policies
                 └─ 06 ports
                     ├─ 07 persistence ──┐
                     └─ 08 gcs client ───┤
                                          └─ 09 config sync
                                              └─ 10 webhook ingestion
                                                  └─ 11 revenue api
                                                      └─ 12 error handling
                                                          └─ 13 observability
                                                              └─ 14 arch + e2e tests
                                                                  └─ 15 docs
```

## Fora do escopo deste repositório

| Item | Destino |
|---|---|
| Provisionamento de infraestrutura (Azure) | Repositório `teslapark-infra`, com Terraform |
| Monitoria gerenciada (Azure Monitor, Application Insights, Grafana) | `teslapark-infra` |
| Pipeline de deploy e ambientes | `teslapark-infra` |
| Autenticação corporativa (Entra ID / OAuth2) | Backlog — a SPEC-12 apenas define o contrato de `401` e `403` |

O que este repositório entrega: **o serviço, seus testes e a infraestrutura local necessária para executá-lo.**
