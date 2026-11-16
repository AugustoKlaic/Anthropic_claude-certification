package br.com.ntconsult.claude.labs.a02p;

import br.com.ntconsult.claude.commons.Claude;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Passo 2 — o mesmo pedido.json, agora pelo SDK. Compare com a saída do passo 1:
 * nenhum cabeçalho escrito à mão, e o objeto que volta é o mesmo JSON, já tipado.
 */
public class ViaSdk {

    public static void executar() throws Exception {
        AnthropicClient client = Claude.client();
        // O pedido.json vira os parâmetros tipados do SDK (o `as MessageCreateParams` do TypeScript).
        MessageCreateParams pedido = MessageCreateParams.builder()
                .body(ObjectMappers.jsonMapper()
                        .readValue(Claude.lerRecurso("labs/a02p/pedido.json"), MessageCreateParams.Body.class))
                .build();

        // a) Sem stream: um objeto só, quando tudo acabar.
        // withRawResponse() dá acesso aos cabeçalhos (de onde sai o request-id); parse() devolve o Message.
        mostrarRequisicao("create()", pedido);
        HttpResponseFor<Message> bruta = client.messages().withRawResponse().create(pedido);
        Message r = bruta.parse();

        System.out.println("RESPOSTA");
        System.out.println("status: " + bruta.statusCode());
        System.out.println("request-id: " + bruta.headers().values("request-id"));
        System.out.println("corpo: " + json(r));
        System.out.println("stop_reason: " + r.stopReason().map(StopReason::asString).orElse("null"));
        System.out.println("content_types: [" + r.content().stream().map(ViaSdk::tipo).collect(Collectors.joining(", ")) + "]");
        System.out.println("usage: " + json(r.usage()));
        System.out.println("texto: " + Claude.texto(r));

        // b) Com stream: os mesmos eventos do passo 1b, um por linha, e a contagem por tipo no fim.
        // O MessageAccumulator monta, no fim, o mesmo objeto do create().
        System.out.println();
        mostrarRequisicao("createStreaming()", pedido);
        System.out.println("RESPOSTA");
        Map<String, Integer> contagem = new LinkedHashMap<>();
        MessageAccumulator acumulador = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(pedido)) {
            stream.stream().forEach(evento -> {
                acumulador.accumulate(evento);
                String tipo = tipo(evento);
                contagem.merge(tipo, 1, Integer::sum);
                // Cada evento numa linha; nos deltas de texto, mostra o pedaço que chegou.
                String pedaco = evento.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .map(texto -> " · texto: \"" + texto.text() + "\"")
                        .orElse("");
                System.out.println("evento: " + tipo + pedaco);
            });
        }
        Message fim = acumulador.message();
        System.out.println("eventos: " + json(contagem));
        System.out.println("stop_reason: " + fim.stopReason().map(StopReason::asString).orElse("null"));
        System.out.println("usage: " + json(fim.usage()));
        System.out.println("texto: " + Claude.texto(fim));
    }

    private static void mostrarRequisicao(String chamada, MessageCreateParams pedido) throws Exception {
        System.out.println("REQUISIÇÃO");
        System.out.println("chamada: client.messages()." + chamada);
        System.out.println("cabeçalhos: (o SDK põe x-api-key, anthropic-version e content-type por você)");
        System.out.println("corpo: " + json(pedido._body()));
        System.out.println();
    }

    private static String json(Object objeto) throws Exception {
        return ObjectMappers.jsonMapper().writeValueAsString(objeto);
    }

    private static String tipo(ContentBlock bloco) {
        if (bloco.isText()) return "text";
        if (bloco.isThinking()) return "thinking";
        if (bloco.isRedactedThinking()) return "redacted_thinking";
        if (bloco.isToolUse()) return "tool_use";
        return "outro";
    }

    private static String tipo(RawMessageStreamEvent evento) {
        if (evento.isMessageStart()) return "message_start";
        if (evento.isContentBlockStart()) return "content_block_start";
        if (evento.isContentBlockDelta()) return "content_block_delta";
        if (evento.isContentBlockStop()) return "content_block_stop";
        if (evento.isMessageDelta()) return "message_delta";
        if (evento.isMessageStop()) return "message_stop";
        return "outro";
    }
}
