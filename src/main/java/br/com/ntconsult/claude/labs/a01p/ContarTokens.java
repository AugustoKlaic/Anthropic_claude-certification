package br.com.ntconsult.claude.labs.a01p;

import br.com.ntconsult.claude.commons.Claude;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.Tool;
import java.util.List;
import java.util.Map;

/** Passo 1 — contar tokens antes de mandar. count_tokens é gratuito e não gera resposta. */
public class ContarTokens {

    private static final String PERGUNTA = "Revise este diff: (vazio)";

    public static void executar() {
        AnthropicClient client = Claude.client();
        String prompt = Claude.lerRecurso("labs/a01p/prompt.md");

        // a) O mesmo prompt em dois modelos: o tokenizer muda do Opus 4.7 em diante.
        contar(client, "prompt.md - claude-opus-5", base("claude-opus-5").system(prompt).addUserMessage(PERGUNTA));
        contar(client, "prompt.md - claude-haiku-4-5", base("claude-haiku-4-5").system(prompt).addUserMessage(PERGUNTA));

        // b) A mesma frase em português e em inglês.
        contar(client, "frase em portugues",
                base("claude-opus-5").addUserMessage("Por que a mesma pergunta dá respostas diferentes?"));
        contar(client, "a mesma frase em ingles",
                base("claude-opus-5").addUserMessage("Why does the same question get different answers?"));

        // c) Uma definição de tool também é entrada — e é cobrada em toda chamada.
        Tool tool = Tool.builder()
                .name("buscar_arquivo")
                .description("Le um arquivo do repositório e devolve o conteúdo com números de linha.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("caminho", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Caminho relativo à raiz do repositório")))
                                .build())
                        .required(List.of("caminho"))
                        .build())
                .build();
        contar(client, "prompt.md + 1 tool - claude-opus-5",
                base("claude-opus-5").system(prompt).addUserMessage(PERGUNTA).addTool(tool));
    }

    private static MessageCountTokensParams.Builder base(String modelo) {
        return MessageCountTokensParams.builder().model(modelo);
    }

    private static void contar(AnthropicClient client, String rotulo, MessageCountTokensParams.Builder params) {
        long inputTokens = client.messages().countTokens(params.build()).inputTokens();
        System.out.printf("%-44s %6d tokens%n", rotulo, inputTokens);
    }
}
