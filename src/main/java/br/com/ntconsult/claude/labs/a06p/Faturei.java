package br.com.ntconsult.claude.labs.a06p;

import br.com.ntconsult.claude.commons.Claude;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * O caso da aula: o suporte do Faturei, um SaaS de notas fiscais (o mesmo da aula 5). Os dados moram
 * em memória. Quem usa este arquivo é o servidor MCP; o script da Skill tem uma cópia dos mesmos
 * dados, porque roda fora do projeto, no Bash do Claude Code. O mesmo conteúdo, entregue de dois jeitos.
 */
public final class Faturei {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Nota(String numero, @JsonProperty("cliente_id") String clienteId, double valor,
            @JsonProperty("emitida_em") String emitidaEm, String status, String motivo) {}

    private record Cliente(@JsonProperty("cliente_id") String clienteId, String nome, String plano, String situacao) {}

    private static final List<Nota> NOTAS = List.of(
            new Nota("1043", "C-31007", 1250, "2026-10-19", "rejeitada", "R-12: CFOP incompatível com destinatário de outro estado"),
            new Nota("1044", "C-31007", 1250, "2026-10-20", "emitida", null),
            new Nota("1051", "C-20417", 89.9, "2026-10-21", "rejeitada", "R-31: cliente com conta suspensa"));

    private static final List<Cliente> CLIENTES = List.of(
            new Cliente("C-31007", "Padaria Estrela Ltda", "Pro", "ativa"),
            new Cliente("C-20417", "Ana Souza MEI", "Essencial", "suspensa por falta de pagamento"));

    private static final ObjectMapper JSON = new ObjectMapper();

    /** O manual de rejeições: conhecimento, não ação. O servidor MCP o serve como resource. */
    public static String rejeicoes() {
        return Claude.lerRecurso("labs/a06p/rejeicoes.md");
    }

    /**
     * Devolve a nota em JSON. Se não existe, lança erro com uma mensagem que diz o que fazer:
     * quem chama devolve isso ao Claude como erro de execução (isError).
     */
    public static String buscarNota(String numero) {
        return NOTAS.stream().filter(n -> n.numero().equals(numero)).findFirst()
                .map(Faturei::json)
                .orElseThrow(() -> new IllegalArgumentException("Nota " + numero + " não encontrada. Os números do Faturei "
                        + "têm 4 dígitos e começam em 1000. Peça ao usuário para conferir o número no PDF da nota."));
    }

    public static String buscarCliente(String clienteId) {
        return CLIENTES.stream().filter(c -> c.clienteId().equals(clienteId)).findFirst()
                .map(Faturei::json)
                .orElseThrow(() -> new IllegalArgumentException("Cliente " + clienteId + " não encontrado. O código tem o "
                        + "formato C-NNNNN e vem no campo cliente_id da nota."));
    }

    private static String json(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Faturei() {}
}
