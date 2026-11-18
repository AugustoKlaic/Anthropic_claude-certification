package br.com.ntconsult.claude.labs.a03p;

import com.anthropic.models.messages.Usage;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * US$ por milhão de tokens, da página Pricing, conferida em 19/09/2026. Preço muda:
 * confira de novo antes de rodar. Escrita de cache de 5 min = 1,25× a entrada;
 * leitura = 0,1× (0,025× só no Fable 5.1). Na Batch API, tudo pela metade.
 */
public final class Precos {

    public record Preco(double entrada, double saida) {}

    /** Os três modelos do lab, na ordem em que o passo 1 os chama. */
    public static final Map<String, Preco> PRECOS = new LinkedHashMap<>();

    static {
        PRECOS.put("claude-haiku-4-5-20251001", new Preco(1, 5));
        PRECOS.put("claude-sonnet-5", new Preco(2, 10));
        PRECOS.put("claude-opus-5", new Preco(5, 25));
    }

    private Precos() {}

    /** Custo de uma chamada a partir do {@code usage} que voltou. {@code fator} 0.5 = Batch API. */
    public static double custo(String modelo, Usage u, double fator) {
        return custo(modelo, u.inputTokens(), u.cacheCreationInputTokens().orElse(0L),
                u.cacheReadInputTokens().orElse(0L), u.outputTokens(), fator);
    }

    /** Custo a partir dos números crus: útil para simular "e se não tivesse cache?". */
    public static double custo(String modelo, long input, long cacheCriacao, long cacheLeitura, long saida,
            double fator) {
        Preco p = PRECOS.get(modelo);
        if (p == null) {
            throw new IllegalArgumentException("sem preço para " + modelo);
        }
        double entrada = input + cacheCriacao * 1.25 + cacheLeitura * 0.1;
        return (entrada * p.entrada() + saida * p.saida()) / 1e6 * fator;
    }

    public static String dolar(double valor) {
        return String.format(Locale.US, "$%.5f", valor);
    }
}
