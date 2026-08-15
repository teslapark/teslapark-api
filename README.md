# teslapark-api

Serviço de gestão de estacionamento: controla a ocupação das vagas, processa os eventos de entrada e saída publicados pelo sistema de cancelas e calcula a receita por setor.

**Stack:** Kotlin 2.1 · JVM 21 · Micronaut 4 · MySQL 8 · Arquitetura Hexagonal

## Documentação

| Documento | Conteúdo |
|---|---|
| [Arquitetura (ADR-0001)](docs/adr/0001-arquitetura-teslapark-api.md) | Decisões técnicas e suas justificativas, modelo de dados, contrato da API e requisitos não funcionais |
| [Especificações](docs/specs.md) | Plano de implementação — escopo, regras e critérios de aceite de cada entrega |

## Ambiente local

```bash
make up
```

Sobe MySQL, o sistema de cancelas e a API. Serviço disponível em `http://localhost:3003`.
