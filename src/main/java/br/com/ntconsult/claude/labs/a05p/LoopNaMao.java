package br.com.ntconsult.claude.labs.a05p;

import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_CLIENTE;
import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_NOTA;
import static br.com.ntconsult.claude.labs.a05p.Faturei.MODELO;
import static br.com.ntconsult.claude.labs.a05p.Faturei.SYSTEM;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.labs.a05p.Loop.Resultado;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.StopReason;
import java.util.List;

/**
 * Passo 1 — o loop na mão, numa pergunta que pede duas tools em sequência: a nota traz o
 * cliente_id, e só com ele dá para buscar o plano.
 */
public class LoopNaMao {

    public static void executar() {
        AnthropicClient client = Claude.client();
        String pergunta = "Por que a nota 1043 foi rejeitada, e em que plano está o cliente dela?";

        // Antes: quanto as tools custam. count_tokens é gratuito.
        long sem = client.messages().countTokens(MessageCountTokensParams.builder()
                .model(MODELO).system(SYSTEM).addUserMessage(pergunta).build()).inputTokens();
        long com = client.messages().countTokens(MessageCountTokensParams.builder()
                .model(MODELO).system(SYSTEM).addUserMessage(pergunta)
                .addTool(BUSCAR_NOTA).addTool(BUSCAR_CLIENTE).build()).inputTokens();
        System.out.println("entrada sem tools: " + sem + " · com as 2 tools: " + com + " · as tools custam "
                + (com - sem) + " tokens em todo pedido do loop");

        Resultado r = Loop.rodar(client, Loop.Opcoes.de(List.of(BUSCAR_NOTA, BUSCAR_CLIENTE), pergunta));
        System.out.println("\n" + r.voltas() + " voltas, " + r.chamadas() + " chamadas de tool, " + r.entrada()
                + " tokens de entrada somando todas as voltas");
        System.out.println("stop_reason final: " + r.resposta().stopReason().map(StopReason::asString).orElse("null"));
    }
}
