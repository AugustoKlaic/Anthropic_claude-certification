---
name: faturei-notas
description: Consulta notas fiscais e clientes do Faturei e explica rejeições de nota. Use quando o usuário citar um número de nota do Faturei, perguntar por que uma nota foi rejeitada ou em que plano está um cliente.
allowed-tools: Bash(java ${CLAUDE_SKILL_DIR}/scripts/Consultar.java *)
---

# Notas do Faturei

## Consultar

Rode o script da skill. Ele devolve JSON, ou uma mensagem de erro que diz o que fazer.

- Nota: `java ${CLAUDE_SKILL_DIR}/scripts/Consultar.java nota <numero>`
- Cliente: `java ${CLAUDE_SKILL_DIR}/scripts/Consultar.java cliente <cliente_id>`

A nota traz o `cliente_id`. Para saber nome, plano ou situação do cliente, consulte o cliente
com esse código. Não invente um número de nota que o usuário não deu.

## Explicar uma rejeição

O motivo da nota começa por um código (R-12, R-31…). O que o cliente faz em cada código está em
[rejeicoes.md](rejeicoes.md). Leia esse arquivo só quando a pergunta for sobre uma rejeição.

Ao explicar ao cliente: até 4 frases, sem jargão fiscal, e termine com o que ele faz agora.
