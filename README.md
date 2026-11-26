# Labs do CCDV-F em Java

Tradução para Java dos labs do centro de estudos Anthropic (originais em TypeScript/Bun), feita um lab por vez.

| Lab | Tema | Situação |
|---|---|---|
| `a01p` | Como o Claude responde | traduzido |
| `a02p` | Anatomia de uma requisição | traduzido |
| `a03p` | Escolher o modelo e pagar a conta | traduzido |
| `a04p` | Prompt que funciona, saída confiável | traduzido |
| `a05p` | Tool use por dentro | traduzido |
| `a06p` | MCP, Skills e tools: quando usar o quê | traduzido |
| `a07p` … `a12p` | | a traduzir |

## Estrutura

Um projeto Gradle simples (Groovy, `build.gradle`), com um único módulo e uma única `Main`:

```
├── build.gradle                       # dependências, cada uma com comentário
├── settings.gradle
└── src/main
    ├── java/br/com/ntconsult/claude
    │   ├── Main.java                  # <- rode este main na IDE (ponto de entrada de todos os labs)
    │   ├── commons/                   # Config (lê o .properties) e Claude (cliente da API)
    │   └── labs/                      # um pacote por lab
    │       ├── a01p/                  # ContarTokens, CincoVezes, Thinking
    │       ├── a02p/                  # ChamadaCrua, ViaSdk, MultiTurno
    │       ├── a03p/                  # Precos, TresModelos, Cache, Lote
    │       ├── a04p/                  # Triagem, TresPrompts, ForcarSchema, Defensivo
    │       ├── a05p/                  # Faturei, Loop, LoopNaMao, Paralelas, Quebrar, Aprovacao
    │       └── a06p/                  # Faturei, Servidor (MCP), Cliente
    └── resources
        ├── config.properties          # SUA chave e as opções dos labs
        └── labs/                      # arquivos de apoio de cada lab
            ├── a01p/prompt.md
            ├── a02p/pedido.json
            ├── a03p/contexto.txt
            └── a06p/rejeicoes.md
```

Fora do `src`, a pasta `claude-code/` guarda o que o **Claude Code** lê (não é código Java do projeto):

```
claude-code/a06p
├── .mcp.json                          # pluga o servidor MCP no Claude Code (passo 2)
└── skill/.claude/skills/faturei-notas # a Skill (passo 3): SKILL.md, rejeicoes.md, scripts/Consultar.java
```

## Como usar

1. Abra o projeto na IDE como projeto Gradle (JDK 21).
2. Em `src/main/resources/config.properties`, preencha `anthropic.api-key` com a sua chave. **Não commite o arquivo com a chave.**
3. Abra `Main.java`, deixe ativos só os passos que quer rodar (comente o resto) e execute o `main`. A saída aparece no console da IDE.

Cada passo faz chamadas reais e cobradas na API (exceto `count_tokens`, que é gratuito).

## Configuração (`config.properties`)

| Chave | Lab | Para que serve |
|---|---|---|
| `anthropic.api-key` | todos | chave da API (obrigatória) |
| `anthropic.base-url` | todos | URL base da API; em branco usa a padrão |
| `a01p.modelo` | a01p | modelo do passo 2 (padrão `claude-opus-5`) |
| `a01p.temperatura` | a01p | temperatura do passo 2; no Opus 5 dá erro 400 |
| `a02p.sem-versao` | a02p | `true` tira o cabeçalho `anthropic-version` da chamada crua |
| `a02p.esquecer` | a02p | `true` manda só a pergunta da vez, sem histórico (passo 3) |
| `a03p.effort` | a03p | `low` ou `medium` no Sonnet 5 e no Opus 5 (passo 1) |
| `a03p.modelo` | a03p | modelo do passo 2 (cache) |
| `a03p.tamanho` | a03p | tamanho do contexto em caracteres (passo 2) |
| `a03p.quebrar` | a03p | `true` quebra o cache de propósito (passo 2) |
| `a03p.lote-id` | a03p | id do lote a buscar; em branco usa o `lote.txt` |
| `a04p.modelo` | a04p | modelo dos três passos (padrão `claude-sonnet-5`) |
| `a04p.versao` | a04p | `ruim`, `melhor` ou `boa`: roda uma versão só no passo 1 |
| `a05p.modelo` | a05p | modelo dos quatro passos (padrão `claude-sonnet-5`) |
| `a05p.serial` | a05p | `true` liga `disable_parallel_tool_use` no passo 2 |
| `a05p.quebra` | a05p | modo do passo 3 (`generico`, `instrutivo`, `texto-antes`, `faltou-um`, `cortada`); em branco roda todos |
| `a05p.teto` | a05p | teto curto do modo `cortada` (padrão 3) |
| `a05p.aprovar` | a05p | `s` ou `n` responde o passo 4 sem perguntar; em branco pergunta no console |

## Labs

### a01p — Como o Claude responde
`ContarTokens` (`count_tokens`, gratuito), `CincoVezes` (a mesma chamada 5 vezes, temperature) e `Thinking` (thinking e effort).

### a02p — Anatomia de uma requisição
Mesma requisição de três jeitos. O `curl` do original virou `HttpClient` do Java, sem o SDK.

| Passo | Classe | O que faz |
|---|---|---|
| 1a | `ChamadaCrua.executar()` | chamada crua; imprime `REQUISIÇÃO` e `RESPOSTA` |
| 1b | `ChamadaCrua.executarStream()` | a mesma chamada com `"stream": true`, evento SSE por evento |
| 2 | `ViaSdk.executar()` | o mesmo `pedido.json` pelo SDK (`create` e `createStreaming`) |
| 3 | `MultiTurno.executar()` | conversa de três turnos; com `a02p.esquecer=true` perde o contexto |

O corpo da requisição está em `resources/labs/a02p/pedido.json`. Experimento extra: `a02p.sem-versao=true`.

### a03p — Escolher o modelo e pagar a conta

| Passo | Classe | O que faz |
|---|---|---|
| 1 | `TresModelos.executar()` | o mesmo prompt no Haiku 4.5, Sonnet 5 e Opus 5: `count_tokens`, tempo até o primeiro texto, tempo total, `usage` e custo |
| 2 | `Cache.executar()` | prompt caching: três perguntas com o mesmo contexto; mostra `cache_creation`, `cache_read` e o custo com e sem cache |
| 3a | `Lote.criar()` | cria um lote de 6 tickets na Batch API e guarda o id em `lote.txt` |
| 3b | `Lote.buscar()` | consulta o status e, se terminou, lista os resultados ligando pelo `custom_id` |

Pontos de atenção:
- **Crie o lote antes de tudo** (`Lote.criar()`). A maioria termina em menos de 1 hora, mas o limite é 24 h. Faça os passos 1 e 2 enquanto ele roda e rode `Lote.buscar()` depois (deixe a linha comentada na `Main` até lá).
- Os preços ficam em `Precos.java` (página *Pricing* conferida em 19/09/2026). **Confira de novo antes de rodar**: se o preço mudou, o custo mostrado fica errado.
- O contexto do passo 2 é `resources/labs/a03p/contexto.txt`: um trecho dos tipos do SDK TypeScript (a partir de `export interface Message`), igual ao do original.
- Variações do passo 2, uma por vez: `a03p.quebrar=true` (cache quebrado fica mais caro que sem cache); `a03p.tamanho=8000` (cabe no mínimo do Opus 5, mas não no do Haiku 4.5, que não cacheia e não avisa); `a03p.modelo=claude-haiku-4-5-20251001`.
- Variação do passo 1: `a03p.effort=low`.

### a04p — Prompt que funciona, saída confiável

Caso: triagem de tickets do Faturei (SaaS de notas fiscais). `Triagem.java` tem os 10 tickets rotulados (a régua), o schema e `ler()`, a leitura defensiva em cinco etapas. O zod do original virou validação à mão com Jackson.

| Passo | Classe | O que faz |
|---|---|---|
| 1 | `TresPrompts.executar()` | prompts ruim, melhor e boa nos 10 tickets (30 chamadas): JSON que abre, categoria, prioridade e as 5 etapas |
| 2 | `ForcarSchema.executar()` | schema derivado do record (`outputConfig(Triagem.class)`, o `parse()` do zod) e JSON Schema à mão em `output_config.format` |
| 3a | `Defensivo.simular()` | sete respostas montadas à mão passam pelas 5 etapas; não chama a API |
| 3b | `Defensivo.executar()` | `max_tokens` 40 (cortada, para na etapa 1) e depois 2000 |

Pontos de atenção:
- Os passos 2a (`ForcarSchema.ver()`) e 3a rodam sem chave e sem custo.
- Passo 3b: `thinking` desligado para o teto de 40 cortar o JSON, e não o thinking. No Sonnet 5 pode; no Opus 5 só com effort `high` ou menor.
- O número do passo 1 muda de rodada para rodada. No original, a "ruim" e a "melhor" davam 0/10 em JSON que abre (embrulhavam num bloco de código) e a "boa" 10/10.
- Passo 2: o schema do SDK Java coloca o `enum` na gramática, mas `cliente_id` vira `string` obrigatória, sem `null`. Compare com o schema à mão.

### a05p — Tool use por dentro

Caso: o suporte do Faturei consultando notas e clientes. O loop de tool use é escrito na mão, sem framework. `Faturei.java` tem as três definições de tool (é só isto que o Claude vê) e `executar()` (o código que roda cada uma). `Loop.java` é o loop em si.

| Passo | Classe | O que faz |
|---|---|---|
| 1 | `LoopNaMao.executar()` | duas tools em sequência (a nota traz o `cliente_id`); mostra o custo das tools com `count_tokens` e as voltas |
| 2 | `Paralelas.executar()` | três notas independentes; o Claude pede as três de uma vez. `a05p.serial=true` compara com uma tool por resposta |
| 3 | `Quebrar.executar()` | erro genérico x instrutivo, mensagem de volta errada (400) e `tool_use` cortado por `max_tokens` |
| 4 | `Aprovacao.executar()` | `faturei_cancelar_nota` não tem volta: o loop pergunta antes de executar |

Pontos de atenção:
- O Claude decide sozinho quantas tools pede por volta. Se no passo 2 ele não pedir as três notas de uma vez, rode de novo.
- Passo 3 sem `a05p.quebra` roda os cinco modos em sequência (uns 10 pedidos). No `faltou-um`, se o Claude pedir só uma tool na primeira volta, a mensagem de volta sai vazia e o erro muda: rode de novo.
- O modo `cortada` desliga o thinking e força a tool: com teto de 3 tokens, o corte cai dentro do `tool_use`. No Opus 5 só dá para desligar o thinking com effort `high` ou menor.
- Passo 4: o `Scanner` lê do console da IDE. Para não digitar, use `a05p.aprovar=s` ou `n`. Rodar o `s` cancela a nota 1045 só em memória; ela volta ao normal na próxima execução.
- Os dados das notas ficam em memória: o passo 4 com `s` e depois outra chamada do `Main` na mesma execução veria a nota já cancelada.

### a06p — MCP, Skills e tools: quando usar o quê

Mesmo caso da aula 5 (Faturei), entregue de dois jeitos: um **servidor MCP** (código Java) e uma **Skill** (pasta para o Claude Code). Os passos 1 e 4 não chamam modelo e não precisam de chave; os passos 2 e 3 usam o Claude Code com o seu login.

A pergunta das duas rodadas: **"Por que a nota 1043 do Faturei foi rejeitada, e em que plano está o cliente dela?"**

| Passo | O que é | Como rodar |
|---|---|---|
| 1 | `Servidor` (MCP, stdio) e `Cliente.executar()`: o cliente inicia o servidor como subprocesso e mostra cada mensagem JSON-RPC (`→`) | pela `Main` |
| 1b | `Cliente.quebrar()`: o servidor escreve no stdout e a resposta se perde (timeout de 5 s) | pela `Main` |
| 2 | plugar o servidor no Claude Code | terminal: veja abaixo |
| 3 | a mesma pergunta com a Skill `faturei-notas` | terminal: veja abaixo |
| 4 | comparar MCP x Skill | quadro no fim desta seção |

**Passo 1** — `Servidor.java` tem as três primitivas: duas tools (`buscar_nota`, `buscar_cliente`), um resource (`faturei://rejeicoes`) e um prompt (`explicar-rejeicao`). O `Servidor` não é chamado pela `Main`: o `Cliente` o inicia (`java -cp ... Servidor`). Pontos para parar na saída:
- A revisão do protocolo aparece (`2025-11-25`) e o cliente abre com `initialize` e `notifications/initialized`.
- **Erro de execução** (nota 9999): volta como resultado com `isError: true` e mensagem que diz o que fazer. **Tool que não existe**: erro JSON-RPC (`-32602`). O SDK Java troca o nome na mensagem por `invalid_tool_name`.
- No stdio, o stdout **é** o canal do JSON-RPC. Log de servidor vai no stderr (`System.err`).

**Passo 2 — plugar no Claude Code.** Uma vez, na raiz do projeto, para copiar as bibliotecas que o `.mcp.json` usa:

```bash
./gradlew copiarLibs
```

Depois, abra o Claude Code **dentro de `claude-code/a06p`** (aberto na raiz do projeto, ele não vê o `.mcp.json`):

```bash
cd claude-code/a06p
claude
```

O servidor de projeto pede aprovação (veio do repositório). Depois `/mcp` mostra `faturei` com 2 tools; faça a pergunta e repare no nome `mcp__faturei__buscar_nota` e no `ToolSearch` antes. O resource: `@` e `faturei:faturei://rejeicoes`. O prompt: `/mcp__faturei__explicar-rejeicao 1043`. O `.mcp.json` usa `;` como separador do classpath (Windows); em Linux/macOS troque por `:`. O mesmo registro sai de `claude mcp add --scope project faturei -- java -cp "..." br.com.ntconsult.claude.labs.a06p.Servidor`.

**Passo 3 — a mesma coisa como Skill.**

```bash
cd claude-code/a06p/skill
claude --strict-mcp-config
```

`--strict-mcp-config` desliga os servidores MCP da sessão. Sem ele, o Claude Code acha o `.mcp.json` da pasta de cima e a comparação deixa de ser justa. Faça a mesma pergunta. O Claude decide sozinho se usa a Skill; se não disparar, comece a pergunta com `/faturei-notas`. O script da Skill é `scripts/Consultar.java`, que roda direto (`java Consultar.java nota 1043`), sem compilar. Ele tem uma cópia dos dados de `Faturei.java`, porque roda fora do projeto.

**Passo 4 — comparar:**

| | Servidor MCP | Skill |
|---|---|---|
| O que dá ao Claude | Tools (ação e dado), resource, prompt | Instrução e conhecimento, mais um script |
| No começo da sessão | Os nomes das tools | A descrição (~70 tokens) |
| Onde o código roda | No processo do servidor | No Bash da sessão |
| Quem instala | Quem configura o cliente (`claude mcp add`, `.mcp.json`) | Quem copia a pasta para `.claude/skills/` |
| Serve a outro app | Qualquer cliente MCP | Cada superfície tem as suas Skills |

Pontos de atenção:
- O SDK MCP Java (`2.0.1`) fala a revisão `2025-11-25`: não tem o `server/discover` nem a revisão `2026-07-28` do SDK TypeScript, então não há o `cliente:2025` do original.
- O SDK MCP loga o handshake e o erro do passo "quebrar". Está desligado em `src/main/resources/simplelogger.properties` (troque `off` por `info` para ver).
- O `rejeicoes.md` existe em dois lugares: `resources/labs/a06p/` (o servidor serve como resource) e dentro da Skill. Mantenha os dois iguais.

## Próximos labs

Crie o pacote `labs/a07p` com as classes do lab, coloque os arquivos de apoio em `src/main/resources/labs/a07p/`, as opções novas no `config.properties` (prefixo `a07p.`) e chame os passos na `Main`. Se precisar de biblioteca nova, adicione no `build.gradle` com um comentário.
