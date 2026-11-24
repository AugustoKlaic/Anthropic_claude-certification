# Labs do CCDV-F em Java

Tradução para Java dos labs do centro de estudos Anthropic (originais em TypeScript/Bun), feita um lab por vez.

| Lab | Tema | Situação |
|---|---|---|
| `a01p` | Como o Claude responde | traduzido |
| `a02p` | Anatomia de uma requisição | traduzido |
| `a03p` | Escolher o modelo e pagar a conta | traduzido |
| `a04p` | Prompt que funciona, saída confiável | traduzido |
| `a05p` … `a12p` | | a traduzir |

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
    │       └── a04p/                  # Triagem, TresPrompts, ForcarSchema, Defensivo
    └── resources
        ├── config.properties          # SUA chave e as opções dos labs
        └── labs/                      # arquivos de apoio de cada lab
            ├── a01p/prompt.md
            ├── a02p/pedido.json
            └── a03p/contexto.txt
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

## Próximos labs

Crie o pacote `labs/a05p` com as classes do lab, coloque os arquivos de apoio em `src/main/resources/labs/a05p/`, as opções novas no `config.properties` (prefixo `a05p.`) e chame os passos na `Main`. Se precisar de biblioteca nova, adicione no `build.gradle` com um comentário.
