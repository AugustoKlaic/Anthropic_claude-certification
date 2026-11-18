package br.com.ntconsult.claude.labs.a03p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Passo 2 — prompt caching. O contexto é um trecho dos tipos da própria API (a interface Message
 * do SDK TypeScript, guardado em resources/labs/a03p/contexto.txt): um documento real, sempre com
 * os mesmos bytes. O breakpoint fica no fim dele, o último bloco que não muda.
 *
 * <p>Em config.properties:
 * <ul>
 *   <li>a03p.modelo=claude-haiku-4-5-20251001 troca o modelo (padrão: claude-opus-5)
 *   <li>a03p.tamanho=8000 corta o contexto em 8 mil caracteres (padrão: 30000)
 *   <li>a03p.quebrar=true põe data e hora no topo do system, antes do breakpoint
 * </ul>
 */
public class Cache {

    private static final List<String> PERGUNTAS = List.of(
            "Quais valores o campo stop_reason pode ter?",
            "O que o campo cache_read_input_tokens conta?",
            "Quais níveis de effort existem?");

    public static void executar() {
        AnthropicClient client = Claude.client();
        String modelo = Config.get("a03p.modelo", "claude-opus-5");
        int tamanho = Integer.parseInt(Config.get("a03p.tamanho", "30000"));
        boolean quebrar = Config.get("a03p.quebrar", "false").equalsIgnoreCase("true");

        String tipos = Claude.lerRecurso("labs/a03p/contexto.txt");
        String contexto = tipos.substring(0, Math.min(tamanho, tipos.length()));

        long prefixo = client.messages()
                .countTokens(MessageCountTokensParams.builder()
                        .model(modelo)
                        .systemOfTextBlockParams(system(contexto, quebrar))
                        .addUserMessage(".")
                        .build())
                .inputTokens();
        System.out.println(modelo + " · contexto de " + tamanho + " caracteres ≈ " + prefixo + " tokens"
                + (quebrar ? " · quebrar=true" : ""));

        double comCache = 0;
        double semCache = 0;
        for (int i = 0; i < PERGUNTAS.size(); i++) {
            String pergunta = PERGUNTAS.get(i);
            // system() é chamado a cada pergunta: com quebrar=true a hora muda a cada chamada como num app de verdade.
            Message r = client.messages().create(MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(16000)
                    .systemOfTextBlockParams(system(contexto, quebrar))
                    .addUserMessage(pergunta)
                    .build());

            Usage u = r.usage();
            long cria = u.cacheCreationInputTokens().orElse(0L);
            long le = u.cacheReadInputTokens().orElse(0L);
            double custo = Precos.custo(modelo, u, 1);
            // Sem cache, toda a entrada seria paga ao preço normal.
            double custoSemCache = Precos.custo(modelo, u.inputTokens() + cria + le, 0, 0, u.outputTokens(), 1);
            comCache += custo;
            semCache += custoSemCache;

            System.out.println("\n=== chamada " + (i + 1) + ": " + pergunta);
            System.out.println("cache_creation_input_tokens: " + cria);
            System.out.println("cache_read_input_tokens: " + le);
            System.out.println("input_tokens: " + u.inputTokens() + "   (só o que vem depois do breakpoint)");
            System.out.println("custo: " + Precos.dolar(custo) + " · sem cache seria " + Precos.dolar(custoSemCache));
            System.out.println("texto: " + Claude.texto(r));
        }
        System.out.println("\nTotal das três: " + Precos.dolar(comCache) + " com cache · "
                + Precos.dolar(semCache) + " sem cache");
    }

    private static List<TextBlockParam> system(String contexto, boolean quebrar) {
        List<TextBlockParam> blocos = new ArrayList<>();
        if (quebrar) {
            blocos.add(TextBlockParam.builder().text("Agora: " + Instant.now()).build());
        }
        blocos.add(TextBlockParam.builder()
                .text("Responda em português, em uma frase, com base nas definições de tipo abaixo.")
                .build());
        // O breakpoint: tudo até aqui (inclusive) é o prefixo que pode ficar em cache.
        blocos.add(TextBlockParam.builder()
                .text(contexto)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build());
        return blocos;
    }
}
