package br.com.ntconsult.claude.commons;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.TextBlock;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Atalhos usados pelos labs. */
public final class Claude {

    private Claude() {}

    /** Cliente da API (o {@code new Anthropic()} do TypeScript), configurado pelo config.properties. */
    public static AnthropicClient client() {
        var builder = AnthropicOkHttpClient.builder().apiKey(Config.required("anthropic.api-key"));
        Config.get("anthropic.base-url").ifPresent(builder::baseUrl);
        return builder.build();
    }

    /** Junta só os blocos de texto da resposta (ignora thinking, tool_use etc.). */
    public static String texto(Message mensagem) {
        return mensagem.content().stream()
                .flatMap(bloco -> bloco.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }

    /** Lê um arquivo de src/main/resources (ex.: "labs/a01p/prompt.md"). */
    public static String lerRecurso(String caminho) {
        try (InputStream in = Claude.class.getClassLoader().getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui ler " + caminho, e);
        }
    }
}
