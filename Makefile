SHELL := /bin/bash
COMPOSE := docker compose
DEV_ENV := API_HOST_PORT=13003 DEBUG_HOST_PORT=15005

.DEFAULT_GOAL := help
.PHONY: help up dev down logs test smoke reset-db build debug

help:
	@echo "teslapark-api"
	@echo ""
	@echo "  make up        Sobe banco (truncado), Gate Control System, monitoria e a API, nessa ordem"
	@echo "  make dev       Mesma ordem, sem a API: ela roda na sua IDE e sincroniza no startup"
	@echo "                 (SKIP_DB_TRUNCATE=1 preserva os dados da execucao anterior)"
	@echo "  make down      Derruba containers, rede e orfaos (preserva o volume do MySQL)"
	@echo "  make logs      Segue os logs de todos os servicos"
	@echo "  make test      Executa a suite completa (./gradlew check)"
	@echo "  make smoke     Roda o smoke test do ambiente containerizado"
	@echo "  make reset-db  Destroi o volume do MySQL e recria o banco vazio"
	@echo "  make build     Reconstroi a imagem da API sem cache"
	@echo "  make debug     Sobe a stack com a JVM da API aberta para debug em localhost:5005"

.env:
	@cp .env.example .env
	@echo ".env criado a partir de .env.example"

# A API e o webhook-forwarder compartilham o namespace de rede do GCS. Reiniciar o GCS
# destroi esse namespace, e um "up" comum apenas inicia os dois sem recria-los, deixando
# ambos presos num namespace morto (so lo, sem rotas). Por isso eles sao recriados sempre.
up: .env
	$(COMPOSE) stop api gate-control-system
	$(COMPOSE) up --detach --wait mysql
	@bash docker/truncate-db.sh
	$(COMPOSE) up --detach --wait gate-control-system prometheus grafana
	$(COMPOSE) up --build --detach --force-recreate api
	@bash docker/wait-for-readiness.sh

dev: .env
	$(COMPOSE) stop gate-control-system
	$(COMPOSE) up --detach --wait mysql
	@bash docker/truncate-db.sh
	$(DEV_ENV) $(COMPOSE) up --detach --wait gate-control-system prometheus grafana
	$(DEV_ENV) $(COMPOSE) --profile local-api up --detach --force-recreate webhook-forwarder
	@echo "dependencias no ar e porta 3003 livre; suba a API na IDE e ela sincroniza o garage no startup"

down:
	$(COMPOSE) --profile local-api down --remove-orphans

logs:
	$(COMPOSE) logs --follow --tail=100

test:
	./gradlew check

smoke: .env
	@bash docker/smoke.sh

reset-db: .env
	$(COMPOSE) rm --stop --force --volumes mysql
	docker volume rm --force teslapark-mysql-data
	$(COMPOSE) up --detach mysql

build: .env
	$(COMPOSE) build --no-cache api

debug: .env
	JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Duser.timezone=UTC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
		$(COMPOSE) up --build --detach --force-recreate gate-control-system api
	@bash docker/wait-for-readiness.sh
	@echo "JVM aguardando o debugger em localhost:5005 (IntelliJ: Remote JVM Debug)"
