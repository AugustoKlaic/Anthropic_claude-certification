package br.com.ntconsult.claude.labs.a05p;

import br.com.ntconsult.claude.commons.Config;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O caso da aula: o suporte do Faturei, um SaaS de notas fiscais, consultando notas e clientes.
 * Os dados moram em memória: o que importa é o loop, não o banco.
 *
 * <p>Aqui estão as três definições de tool (é só isto que o Claude vê) e {@link #executar}, o código
 * que roda cada uma (isso o Claude nunca vê, só o que volta).
 */
public final class Faturei {

    public static final String MODELO = Config.get("a05p.modelo", "claude-sonnet-5");

    public static final String SYSTEM = "Você é o assistente de suporte do Faturei, um SaaS de notas fiscais. "
            + "Responda em português, em poucas frases, com os dados que as tools trouxerem. "
            + "Só cancele uma nota quando o usuário pedir o cancelamento de uma nota específica.";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Nota(String numero, @JsonProperty("cliente_id") String clienteId, double valor,
            @JsonProperty("emitida_em") String emitidaEm, String status, String motivo) {}

    private record Cliente(@JsonProperty("cliente_id") String clienteId, String nome, String plano, String situacao) {}

    private static final List<Nota> NOTAS = new ArrayList<>(List.of(
            new Nota("1043", "C-31007", 1250, "2026-10-19", "rejeitada", "CFOP 5102 incompatível com destinatário de outro estado"),
            new Nota("1044", "C-31007", 1250, "2026-10-20", "emitida", null),
            new Nota("1045", "C-31007", 1250, "2026-10-20", "emitida", null),
            new Nota("1051", "C-20417", 89.9, "2026-10-21", "emitida", null)));

    private static final List<Cliente> CLIENTES = List.of(
            new Cliente("C-31007", "Padaria Estrela Ltda", "Pro", "ativa"),
            new Cliente("C-20417", "Ana Souza MEI", "Essencial", "suspensa por falta de pagamento"));

    private static final ObjectMapper JSON = new ObjectMapper();

    // As definições: descrição de 3 a 4 frases (o que faz, quando usar, quando não, o que volta),
    // cada parâmetro descrito, strict: true (camada 2 do desenho).
    public static final Tool BUSCAR_NOTA = tool(
            "faturei_buscar_nota",
            "Busca uma nota fiscal do Faturei pelo número e devolve cliente_id, valor, data de emissão, status "
                    + "(emitida, rejeitada ou cancelada) e, se rejeitada, o motivo. Use quando o usuário citar um número "
                    + "de nota ou perguntar por que uma nota foi rejeitada. Não traz nome, plano nem situação do cliente: "
                    + "para isso, use faturei_buscar_cliente com o cliente_id que esta tool devolve. "
                    + "Não invente um número que o usuário não deu.",
            Map.of("numero", "Número da nota, com 4 dígitos. Ex.: 1043"));

    public static final Tool BUSCAR_CLIENTE = tool(
            "faturei_buscar_cliente",
            "Busca um cliente do Faturei pelo cliente_id e devolve nome, plano (Essencial ou Pro) e situação da conta. "
                    + "Use quando o usuário perguntar pelo plano ou pela situação de um cliente, ou depois de "
                    + "faturei_buscar_nota, para saber de quem é a nota. Não lista as notas do cliente.",
            Map.of("cliente_id", "Código do cliente no formato C-NNNNN. Ex.: C-31007"));

    public static final Tool CANCELAR_NOTA = tool(
            "faturei_cancelar_nota",
            "Cancela uma nota fiscal emitida. É irreversível: a nota cancelada não volta a valer. Use só quando o "
                    + "usuário pedir o cancelamento de uma nota específica, e diga o motivo que ele deu. Uma pessoa da "
                    + "equipe aprova cada cancelamento antes de ele acontecer; se ela negar, o resultado volta como erro.",
            orderedMap("numero", "Número da nota a cancelar, com 4 dígitos.",
                    "motivo", "O motivo do cancelamento, nas palavras do usuário."));

    /** As tools que não têm volta. O loop pede aprovação humana antes de executar uma delas. */
    public static final Set<String> DESTRUTIVAS = Set.of(CANCELAR_NOTA.name());

    private Faturei() {}

    /**
     * Monta uma Tool com um schema de parâmetros todos string e obrigatórios.
     * É o {@code input_schema} do TypeScript, sem a verbosidade.
     */
    private static Tool tool(String nome, String descricao, Map<String, String> parametros) {
        var propriedades = Tool.InputSchema.Properties.builder();
        parametros.forEach((nomeParam, desc) ->
                propriedades.putAdditionalProperty(nomeParam, JsonValue.from(Map.of("type", "string", "description", desc))));
        return Tool.builder()
                .name(nome)
                .description(descricao)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propriedades.build())
                        .required(List.copyOf(parametros.keySet()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)
                .build();
    }

    private static Map<String, String> orderedMap(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    /**
     * O seu código: o Claude nunca vê isto, só o que volta. Erro é lançado com uma mensagem que diz
     * o que fazer; o loop devolve com is_error. {@code falha} simula o serviço de notas fora do ar.
     */
    public static String executar(String nome, JsonValue entrada, String falha) {
        Map<String, String> i = JSON.convertValue(entrada, new TypeReference<Map<String, String>>() {});
        try {
            if (nome.equals(BUSCAR_NOTA.name())) {
                if (falha != null) throw new IllegalStateException(falha);
                Nota nota = achar(i.get("numero"));
                if (nota == null) {
                    throw new IllegalStateException("Nota " + i.get("numero") + " não encontrada. Os números do Faturei têm "
                            + "4 dígitos e começam em 1000. Peça ao usuário para conferir o número no PDF da nota.");
                }
                return JSON.writeValueAsString(nota);
            }
            if (nome.equals(BUSCAR_CLIENTE.name())) {
                Cliente cliente = CLIENTES.stream().filter(c -> c.clienteId().equals(i.get("cliente_id"))).findFirst().orElse(null);
                if (cliente == null) {
                    throw new IllegalStateException("Cliente " + i.get("cliente_id") + " não encontrado. O código tem o formato "
                            + "C-NNNNN e vem no campo cliente_id de faturei_buscar_nota.");
                }
                return JSON.writeValueAsString(cliente);
            }
            if (nome.equals(CANCELAR_NOTA.name())) {
                Nota nota = achar(i.get("numero"));
                if (nota == null) {
                    throw new IllegalStateException("Nota " + i.get("numero") + " não encontrada. Nada foi cancelado.");
                }
                if (!nota.status().equals("emitida")) {
                    throw new IllegalStateException("A nota " + i.get("numero") + " está " + nota.status()
                            + ": só nota emitida pode ser cancelada. Nada foi cancelado.");
                }
                NOTAS.set(NOTAS.indexOf(nota), new Nota(nota.numero(), nota.clienteId(), nota.valor(), nota.emitidaEm(), "cancelada", null));
                Map<String, String> resultado = new LinkedHashMap<>();
                resultado.put("numero", nota.numero());
                resultado.put("status", "cancelada");
                resultado.put("motivo", i.get("motivo"));
                return JSON.writeValueAsString(resultado);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("Tool " + nome + " não existe.");
    }

    private static Nota achar(String numero) {
        return NOTAS.stream().filter(n -> n.numero().equals(numero)).findFirst().orElse(null);
    }
}
