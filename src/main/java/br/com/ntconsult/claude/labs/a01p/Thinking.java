package br.com.ntconsult.claude.labs.a01p;

import br.com.ntconsult.claude.commons.Claude;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Passo 3 — a mesma pergunta difícil com e sem thinking, e com effort diferente.
 * Compare os blocos que voltam em content, o usage e o tempo.
 */
public class Thinking {

    private static final String PERGUNTA =
            "Um job roda a cada 7 minutos e outro a cada 12, os dois começando às 09:00. "
                    + "Quantas vezes eles rodam no mesmo minuto entre 09:00 e 18:00, inclusive? "
                    + "Responda só o número e uma linha de justificativa.";

    /** Uma rodada: o rótulo e o que ela muda na requisição. */
    private record Rodada(String rotulo, Consumer<MessageCreateParams.Builder> config) {}

    public static void executar() throws Exception {
        AnthropicClient client = Claude.client();

        List<Rodada> rodadas = List.of(
                new Rodada("thinking desligado", p -> p.thinking(ThinkingConfigDisabled.builder().build())),
                new Rodada("adaptive - effort low", p -> adaptive(p, OutputConfig.Effort.LOW)),
                new Rodada("adaptive - effort high", p -> adaptive(p, OutputConfig.Effort.HIGH)));

        for (Rodada rodada : rodadas) {
            // Thinking cabe dentro do max_tokens: 16000 evita parar em max_tokens no effort high.
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model("claude-opus-5")
                    .maxTokens(16000)
                    .addUserMessage(PERGUNTA);
            rodada.config().accept(params);

            long inicio = System.nanoTime();
            Message r = client.messages().create(params.build());
            String segundos = String.format("%.1f", (System.nanoTime() - inicio) / 1e9);

            System.out.printf("%n=== %s - %ss - stop_reason=%s%n", rodada.rotulo(), segundos,
                    r.stopReason().map(StopReason::asString).orElse("null"));
            System.out.println("content: [" + r.content().stream().map(Thinking::tipo).collect(Collectors.joining(", ")) + "]");
            // usage.output_tokens inclui o thinking; output_tokens_details.thinking_tokens mostra quanto.
            System.out.println("usage: " + ObjectMappers.jsonMapper().writeValueAsString(r.usage()));
            System.out.println(Claude.texto(r));
        }
    }

    private static void adaptive(MessageCreateParams.Builder p, OutputConfig.Effort effort) {
        p.thinking(ThinkingConfigAdaptive.builder()
                        .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
                        .build())
                .outputConfig(OutputConfig.builder().effort(effort).build());
    }

    private static String tipo(ContentBlock bloco) {
        if (bloco.isText()) return "text";
        if (bloco.isThinking()) return "thinking";
        if (bloco.isRedactedThinking()) return "redacted_thinking";
        if (bloco.isToolUse()) return "tool_use";
        return "outro";
    }
}
