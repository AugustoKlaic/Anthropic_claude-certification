package br.com.ntconsult.claude.labs.a02p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Passo 1 — a chamada crua, sem o SDK da Anthropic (o equivalente em Java do {@code curl}).
 * O corpo é o pedido.json e os cabeçalhos são escritos à mão.
 *
 * <ul>
 *   <li>{@link #executar()}: resposta inteira de uma vez (passo 1a)
 *   <li>{@link #executarStream()}: a mesma chamada com "stream": true, evento por evento (passo 1b)
 * </ul>
 */
public class ChamadaCrua {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PEDIDO = "labs/a02p/pedido.json";

    /** Passo 1a: resposta inteira. */
    public static void executar() throws Exception {
        HttpResponse<String> resposta = enviar(corpoDoPedido(false), HttpResponse.BodyHandlers.ofString());

        System.out.println("RESPOSTA");
        System.out.println("status: " + resposta.statusCode() + " (" + resposta.version() + ")");
        System.out.println("request-id: " + resposta.headers().firstValue("request-id").orElse("(não veio)"));
        System.out.println("corpo: " + resposta.body());

        if (resposta.statusCode() != 200) {
            return; // erro: o corpo acima já mostra o type, a message e o request_id
        }

        JsonNode corpo = JSON.readTree(resposta.body());
        List<String> tipos = new ArrayList<>();
        corpo.get("content").forEach(bloco -> tipos.add(bloco.get("type").asText()));
        System.out.println("stop_reason: " + corpo.get("stop_reason").asText());
        System.out.println("content_types: " + tipos);
        System.out.println("usage: " + corpo.get("usage"));
        corpo.get("content").forEach(bloco -> {
            if ("text".equals(bloco.get("type").asText())) {
                System.out.println("texto: " + bloco.get("text").asText());
            }
        });
    }

    /** Passo 1b: a mesma chamada em streaming. Cada linha SSE aparece quando chega. */
    public static void executarStream() throws Exception {
        HttpResponse<Stream<String>> resposta = enviar(corpoDoPedido(true), HttpResponse.BodyHandlers.ofLines());
        System.out.println("RESPOSTA");
        System.out.println("status: " + resposta.statusCode() + " (" + resposta.version() + ")");
        resposta.body().forEach(System.out::println);
    }

    private static String corpoDoPedido(boolean stream) throws Exception {
        ObjectNode pedido = (ObjectNode) JSON.readTree(Claude.lerRecurso(PEDIDO));
        if (stream) {
            pedido.put("stream", true);
        }
        return JSON.writeValueAsString(pedido);
    }

    private static <T> HttpResponse<T> enviar(String corpo, HttpResponse.BodyHandler<T> tratador) throws Exception {
        String base = Config.get("anthropic.base-url", "https://api.anthropic.com");
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create(base + "/v1/messages"))
                // Os cabeçalhos que o SDK normalmente põe por você:
                .header("x-api-key", Config.required("anthropic.api-key"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));

        // Experimento extra: a02p.sem-versao=true tira o anthropic-version e a API recusa a chamada.
        if (!Config.get("a02p.sem-versao", "false").equalsIgnoreCase("true")) {
            requisicao.header("anthropic-version", "2023-06-01");
        }

        HttpRequest pronta = requisicao.build();
        mostrarRequisicao(pronta, corpo);
        return HttpClient.newHttpClient().send(pronta, tratador);
    }

    private static void mostrarRequisicao(HttpRequest requisicao, String corpo) {
        System.out.println("REQUISIÇÃO");
        System.out.println(requisicao.method() + " " + requisicao.uri());
        requisicao.headers().map().forEach((nome, valores) -> {
            String valor = nome.equalsIgnoreCase("x-api-key") ? "********" : String.join(", ", valores);
            System.out.println(nome + ": " + valor);
        });
        System.out.println("corpo: " + corpo);
        System.out.println();
    }
}
