# Labs do CCDV-F em Java

Tradução para Java dos labs do centro de estudos Anthropic (originais em TypeScript/Bun), feita um lab por vez.

| Lab | Tema | Situação |
|---|---|---|
| `a01p` | Como o Claude responde | traduzido |
| `a02p` … `a12p` | | a traduzir |

## Estrutura

Um projeto Gradle simples (Groovy, `build.gradle`), com um único módulo:

```
├── build.gradle                  # dependências, cada uma com comentário
├── settings.gradle
└── src/main
    ├── java/br/com/ntconsult/claude
    │   ├── comum/                # Config (lê o .properties) e Claude (cliente da API)
    │   └── labs/
    │   |   └── a01p/             # um pacote por lab
    │   |       ├── ContarTokens.java
    │   |       ├── CincoVezes.java
    │   |       └── Thinking.java
    │   ├── Main.java     # <- rode este main na IDE
    └── resources
        ├── config.properties.example   # modelo: copie para config.properties
        ├── config.properties           # SUA chave (no .gitignore)
        └── labs/a01p/prompt.md         # arquivos de apoio de cada lab
```

## Como usar

1. Abra o projeto na IDE como projeto Gradle (JDK 21).
2. Abra `labs/a01p/Main.java` e rode o `main`. A saída aparece no console da IDE.
