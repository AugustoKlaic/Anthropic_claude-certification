package br.com.ntconsult.claude.labs.a03p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.OutputTokensDetails;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import java.util.Optional;

/**
 * Passo 1 — o mesmo pedido nos três modelos. Antes de gastar, count_tokens (gratuito) estima a
 * entrada em cada um; depois a chamada de verdade, com stream, mede o tempo até o primeiro texto,
 * o tempo total, os tokens e o custo.
 *
 * <p>Em config.properties, a03p.effort=low (ou medium) baixa o effort do Sonnet 5 e do Opus 5.
 * O Haiku 4.5 não tem effort.
 */
public class TresModelos {

    private static final String SYSTEM =
            "Você revisa código TypeScript. Responda em português, em no máximo três frases.";

    private static final String PERGUNTA = """
            Esta função devolve o total certo? Se não, por quê?

            async function totalPedidos(ids: string[]) {
              let total = 0
              ids.forEach(async (id) => {
                const pedido = await buscarPedido(id)
                total += pedido.valor
              })
              return total
            }""";

    public static void executar() {
        AnthropicClient client = Claude.client();
        Optional<OutputConfig.Effort> effort = Config.get("a03p.effort").map(OutputConfig.Effort::of);

        for (String modelo : Precos.PRECOS.keySet()) {
            boolean comEffort = effort.isPresent() && !modelo.startsWith("claude-haiku");

            long estimado = client.messages()
                    .countTokens(MessageCountTokensParams.builder()
                            .model(modelo)
                            .system(SYSTEM)
                            .addUserMessage(PERGUNTA)
                            .build())
                    .inputTokens();

            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(16000)
                    .system(SYSTEM)
                    .addUserMessage(PERGUNTA);
            if (comEffort) {
                params.outputConfig(OutputConfig.builder().effort(effort.get()).build());
            }

            long inicio = System.nanoTime();
            long primeiroTexto = -1;
            MessageAccumulator acumulador = MessageAccumulator.create();
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params.build())) {
                for (RawMessageStreamEvent evento : (Iterable<RawMessageStreamEvent>) stream.stream()::iterator) {
                    acumulador.accumulate(evento);
                    boolean ehTexto = evento.contentBlockDelta()
                            .flatMap(d -> d.delta().text())
                            .isPresent();
                    if (primeiroTexto < 0 && ehTexto) {
                        primeiroTexto = System.nanoTime();
                    }
                }
            }
            long fim = System.nanoTime();
            Message r = acumulador.message();

            long pensou = r.usage().outputTokensDetails().map(OutputTokensDetails::thinkingTokens).orElse(0L);
            long msPrimeiro = ((primeiroTexto < 0 ? fim : primeiroTexto) - inicio) / 1_000_000;
            long msTotal = (fim - inicio) / 1_000_000;

            System.out.println("\n=== " + modelo + (comEffort ? " · effort " + effort.get().asString() : ""));
            System.out.println("count_tokens: " + estimado + " de entrada, antes de chamar");
            System.out.println("usage: " + r.usage().inputTokens() + " de entrada · " + r.usage().outputTokens()
                    + " de saída (" + pensou + " de thinking)");
            System.out.println("tempo: primeiro texto em " + msPrimeiro + " ms · total " + msTotal + " ms");
            System.out.println("custo: " + Precos.dolar(Precos.custo(modelo, r.usage(), 1)));
            System.out.println("stop_reason: " + r.stopReason().map(StopReason::asString).orElse("null"));
            System.out.println("texto: " + Claude.texto(r));
        }
    }
}
