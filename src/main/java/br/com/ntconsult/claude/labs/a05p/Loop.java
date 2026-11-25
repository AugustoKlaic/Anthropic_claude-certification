package br.com.ntconsult.claude.labs.a05p;

import static br.com.ntconsult.claude.labs.a05p.Faturei.DESTRUTIVAS;
import static br.com.ntconsult.claude.labs.a05p.Faturei.MODELO;
import static br.com.ntconsult.claude.labs.a05p.Faturei.SYSTEM;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * O loop de tool use escrito na mão: a camada 1 do desenho em código.
 * Enquanto stop_reason for "tool_use": executar cada tool, devolver os tool_result e pedir de novo.
 */
public final class Loop {

    /**
     * O que muda de um passo para outro.
     *
     * @param aprovar chamada antes de toda tool destrutiva; sem ela, a ação é negada
     * @param falha passo 3: faz o serviço de notas falhar com esta mensagem
     */
    public record Opcoes(List<Tool> tools, String pergunta, ToolChoice toolChoice,
            Predicate<ToolUseBlock> aprovar, String falha) {

        public static Opcoes de(List<Tool> tools, String pergunta) {
            return new Opcoes(tools, pergunta, null, null, null);
        }

        public Opcoes comToolChoice(ToolChoice t) {
            return new Opcoes(tools, pergunta, t, aprovar, falha);
        }

        public Opcoes comAprovacao(Predicate<ToolUseBlock> a) {
            return new Opcoes(tools, pergunta, toolChoice, a, falha);
        }

        public Opcoes comFalha(String f) {
            return new Opcoes(tools, pergunta, toolChoice, aprovar, f);
        }
    }

    public record Resultado(Message resposta, int voltas, int chamadas, long entrada) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private Loop() {}

    public static Resultado rodar(AnthropicClient client, Opcoes o) {
        // max_tokens folgado: no Sonnet 5 e no Opus 5 o thinking vem ligado e conta dentro dele.
        MessageCreateParams.Builder pedido = MessageCreateParams.builder()
                .model(MODELO)
                .maxTokens(16000)
                .system(SYSTEM)
                .addUserMessage(o.pergunta());
        o.tools().forEach(pedido::addTool);
        if (o.toolChoice() != null) {
            pedido.toolChoice(o.toolChoice());
        }

        Message r = client.messages().create(pedido.build());
        int voltas = 1;
        int chamadas = 0;
        long entrada = r.usage().inputTokens();
        mostrar(voltas, r);

        while (r.stopReason().filter(StopReason.TOOL_USE::equals).isPresent()) {
            // A resposta volta inteira: thinking, texto e tool_use. Filtrar ou editar o thinking dá 400.
            pedido.addMessage(r);

            List<ToolUseBlock> pedidas = r.content().stream().flatMap(b -> b.toolUse().stream()).toList();
            chamadas += pedidas.size();
            // Só leitura: pode rodar em paralelo. Com efeito colateral: uma de cada vez, na ordem.
            boolean soLeitura = pedidas.stream().noneMatch(b -> DESTRUTIVAS.contains(b.name()));
            List<ToolResultBlockParam> resultados = new ArrayList<>();
            if (soLeitura) {
                // parallelStream + toList() mantém a ordem dos tool_use.
                resultados.addAll(pedidas.parallelStream().map(b -> rodarUma(b, o)).toList());
            } else {
                for (ToolUseBlock b : pedidas) {
                    resultados.add(rodarUma(b, o));
                }
            }

            // Um tool_result para cada tool_use, todos numa mensagem só, e nada antes deles.
            pedido.addUserMessageOfBlockParams(resultados.stream().map(ContentBlockParam::ofToolResult).toList());
            r = client.messages().create(pedido.build());
            mostrar(++voltas, r);
            entrada += r.usage().inputTokens();
        }
        // Saiu do while: end_turn é a resposta. max_tokens, refusal e os outros têm o seu ramo (aula 2).
        return new Resultado(r, voltas, chamadas, entrada);
    }

    private static ToolResultBlockParam rodarUma(ToolUseBlock b, Opcoes o) {
        ToolResultBlockParam.Builder r = ToolResultBlockParam.builder().toolUseId(b.id());
        String conteudo;
        boolean erro;
        if (DESTRUTIVAS.contains(b.name()) && (o.aprovar() == null || !o.aprovar().test(b))) {
            // Negou: mesmo assim vai um tool_result, com is_error e o que fazer.
            conteudo = "A pessoa responsável negou esta ação, e nada foi feito. Não tente de novo: "
                    + "diga ao usuário que o pedido não foi aprovado.";
            erro = true;
        } else {
            try {
                conteudo = Faturei.executar(b.name(), b._input(), o.falha());
                erro = false;
            } catch (RuntimeException e) {
                // A exceção não derruba o loop: vira um tool_result com is_error e a mensagem.
                conteudo = e.getMessage();
                erro = true;
            }
        }
        r.content(conteudo);
        if (erro) {
            r.isError(true);
        }
        System.out.println("   → tool_result " + sufixo(b.id()) + (erro ? " (is_error)" : "") + ": " + conteudo);
        return r.build();
    }

    public static void mostrar(int volta, Message r) {
        System.out.println("\n── volta " + volta + " · stop_reason: " + r.stopReason().map(StopReason::asString).orElse("null")
                + " · entrada " + r.usage().inputTokens() + " · saída " + r.usage().outputTokens());
        for (ContentBlock b : r.content()) {
            if (b.isThinking()) {
                System.out.println("   [thinking]");
            } else if (b.isText()) {
                System.out.println("   texto: " + b.asText().text());
            } else if (b.isToolUse()) {
                ToolUseBlock t = b.asToolUse();
                System.out.println("   tool_use " + sufixo(t.id()) + ": " + t.name() + " " + json(t));
            } else {
                System.out.println("   [outro bloco]");
            }
        }
    }

    private static String json(ToolUseBlock t) {
        try {
            return JSON.writeValueAsString(t._input());
        } catch (Exception e) {
            return String.valueOf(t._input());
        }
    }

    private static String sufixo(String id) {
        return id.substring(Math.max(0, id.length() - 6));
    }
}
