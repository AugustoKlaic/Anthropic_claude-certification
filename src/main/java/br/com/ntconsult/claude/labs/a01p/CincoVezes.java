package br.com.ntconsult.claude.labs.a01p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Passo 2 — a mesma requisição, cinco vezes. Quantas respostas diferentes voltam?
 *
 * <p>Em config.properties, a01p.modelo troca o modelo e a01p.temperatura envia temperature
 * (só aceita em modelos antigos, ex.: claude-haiku-4-5). Do Opus 4.7 em diante, temperature
 * fora do padrão dá 400 — experimente e leia o erro.
 */
public class CincoVezes {

    public static void executar() {
        AnthropicClient client = Claude.client();
        String modelo = Config.get("a01p.modelo", "claude-opus-5");
        Optional<Double> temperatura = Config.get("a01p.temperatura").map(Double::parseDouble);

        List<String> respostas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(2000)
                    .addUserMessage("Em uma frase, explique para um dev o que é um token num LLM.");
            temperatura.ifPresent(params::temperature);

            Message r;
            try {
                r = client.messages().create(params.build());
            } catch (AnthropicServiceException e) {
                // O 400 de "temperature is deprecated" cai aqui: mostramos o erro e paramos o passo.
                System.out.println("ERRO " + e.statusCode() + " " + json(e));
                return;
            }

            String texto = Claude.texto(r);
            respostas.add(texto);
            System.out.printf("%n#%d - stop_reason=%s - output_tokens=%d%n%s%n",
                    i, r.stopReason().map(StopReason::asString).orElse("null"), r.usage().outputTokens(), texto);
        }

        long distintas = new HashSet<>(respostas).size();
        System.out.printf("%n%s%s: %d resposta(s) distinta(s) em 5.%n",
                modelo, temperatura.map(t -> " - temperature=" + t).orElse(""), distintas);
    }

    private static String json(AnthropicServiceException e) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(e.body());
        } catch (Exception ex) {
            return String.valueOf(e.body());
        }
    }
}
