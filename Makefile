SHELL := /bin/bash
COMPOSE := docker compose

.DEFAULT_GOAL := help
.PHONY: help up down logs test smoke reset-db build

help:
	@echo "teslapark-api"
	@echo ""
	@echo "  make up        Sobe MySQL + Gate Control System + API e aguarda readiness"
	@echo "  make down      Derruba containers, rede e orfaos (preserva o volume do MySQL)"
	@echo "  make logs      Segue os logs de todos os servicos"
	@echo "  make test      Executa a suite completa (./gradlew check)"
	@echo "  make smoke     Roda o smoke test do ambiente containerizado"
	@echo "  make reset-db  Destroi o volume do MySQL e recria o banco vazio"
	@echo "  make build     Reconstroi a imagem da API sem cache"

.env:
	@cp .env.example .env
	@echo ".env criado a partir de .env.example"

up: .env
	$(COMPOSE) up --build --detach
	@bash docker/wait-for-readiness.sh

down:
	$(COMPOSE) down --remove-orphans

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
