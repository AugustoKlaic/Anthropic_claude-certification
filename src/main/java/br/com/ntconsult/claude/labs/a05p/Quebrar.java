package br.com.ntconsult.claude.labs.a05p;

import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_CLIENTE;
import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_NOTA;
import static br.com.ntconsult.claude.labs.a05p.Faturei.MODELO;
import static br.com.ntconsult.claude.labs.a05p.Faturei.SYSTEM;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.ToolResultBlockParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passo 3 — quebrar de propósito, um jeito por vez. Em config.properties, a05p.quebra=&lt;modo&gt;
 * roda um modo só; em branco roda todos, na ordem da tabela abaixo.
 */
public class Quebrar {

    private static final Map<String, String> MODOS = new LinkedHashMap<>();

    static {
        MODOS.put("generico", "o serviço de notas cai, e a tool devolve só \"failed\"");
        MODOS.put("instrutivo", "o serviço de notas cai, e a tool diz o que houve e o que fazer");
        MODOS.put("texto-antes", "um texto antes dos tool_result na mensagem de volta");
        MODOS.put("faltou-um", "um dos tool_result fica de fora");
        MODOS.put("cortada", "max_tokens tão curto que corta o tool_use no meio (a05p.teto muda o teto curto)");
    }

    public static void executar() {
        AnthropicClient client = Claude.client();
        String escolhido = Config.get("a05p.quebra", "");
        if (!escolhido.isBlank() && !MODOS.containsKey(escolhido)) {
            System.out.println("Modo desconhecido: " + escolhido + ". Os modos: " + MODOS.keySet());
            return;
        }
        for (var modo : MODOS.entrySet()) {
            if (!escolhido.isBlank() && !modo.getKey().equals(escolhido)) continue;
            System.out.println("\n=== QUEBRA " + modo.getKey() + ": " + modo.getValue());
            switch (modo.getKey()) {
                case "generico", "instrutivo" -> falhaDoServico(client, modo.getKey());
                case "cortada" -> cortada(client);
                default -> mensagemDeVoltaErrada(client, modo.getKey());
            }
        }
    }

    /** O erro vira tool_result com is_error. Compare o que o Claude faz com cada mensagem. */
    private static void falhaDoServico(AnthropicClient client, String modo) {
        String falha = modo.equals("generico")
                ? "failed"
                : "O serviço de notas está fora do ar (503) desde 18h02. Não tente de novo nesta conversa: diga ao "
                        + "usuário que a consulta de notas volta em alguns minutos e ofereça abrir um chamado.";
        var r = Loop.rodar(client, Loop.Opcoes.de(List.of(BUSCAR_NOTA, BUSCAR_CLIENTE), "Por que a nota 1043 foi rejeitada?")
                .comFalha(falha));
        System.out.println("\nstop_reason final: " + r.resposta().stopReason().map(StopReason::asString).orElse("null"));
    }

    /**
     * Thinking desligado e a tool forçada: o teto curto cai dentro do tool_use, não no thinking nem num
     * texto antes. No Sonnet 5 dá para desligar o thinking. No Opus 5, só com effort high ou menor.
     */
    private static void cortada(AnthropicClient client) {
        long tetoCurto = Long.parseLong(Config.get("a05p.teto", "3"));
        for (long teto : new long[] {tetoCurto, 1000}) {
            Message r = client.messages().create(MessageCreateParams.builder()
                    .model(MODELO)
                    .maxTokens(teto)
                    .system(SYSTEM)
                    .addTool(BUSCAR_NOTA)
                    .addTool(BUSCAR_CLIENTE)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name(BUSCAR_NOTA.name()).build()))
                    .addUserMessage("Por que a nota 1043 foi rejeitada?")
                    .build());
            System.out.println("\nmax_tokens: " + teto);
            Loop.mostrar(1, r);
            if (!r.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) break;
            System.out.println("   ✘ cortada: não executar o tool_use que veio. Repetir com max_tokens maior.");
        }
    }

    /** Uma volta normal, e a mensagem de volta montada errada de propósito. */
    private static void mensagemDeVoltaErrada(AnthropicClient client, String modo) {
        MessageCreateParams.Builder pedido = MessageCreateParams.builder()
                .model(MODELO)
                .maxTokens(16000)
                .system(SYSTEM)
                .addTool(BUSCAR_NOTA)
                .addTool(BUSCAR_CLIENTE)
                .addUserMessage("Qual o status das notas 1043 e 1051?");
        Message r = client.messages().create(pedido.build());
        Loop.mostrar(1, r);

        List<ContentBlockParam> resultados = new ArrayList<>(r.content().stream()
                .flatMap(b -> b.toolUse().stream())
                .map(b -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(b.id()).content("{\"status\": \"consultado\"}").build()))
                .toList());
        List<ContentBlockParam> volta = new ArrayList<>();
        if (modo.equals("texto-antes")) {
            volta.add(ContentBlockParam.ofText("Seguem os resultados:"));
            volta.addAll(resultados);
        } else if (!resultados.isEmpty()) {
            volta.addAll(resultados.subList(0, resultados.size() - 1)); // faltou-um: o último fica de fora
        }
        pedido.addMessage(r).addUserMessageOfBlockParams(volta);
        try {
            client.messages().create(pedido.build());
            System.out.println("\nPassou: confira se a resposta acima trouxe mesmo dois tool_use.");
        } catch (BadRequestException e) {
            System.out.println("\n✘ " + e.statusCode() + ": " + e.getMessage());
        }
    }
}
