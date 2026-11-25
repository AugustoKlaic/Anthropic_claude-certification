package br.com.ntconsult.claude.labs.a05p;

import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_CLIENTE;
import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_NOTA;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import br.com.ntconsult.claude.labs.a05p.Loop.Opcoes;
import br.com.ntconsult.claude.labs.a05p.Loop.Resultado;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ToolChoiceAuto;
import java.util.List;

/**
 * Passo 2 — chamadas paralelas. Três notas independentes: o Claude pode pedir as três de uma vez,
 * e o loop roda as três em paralelo e devolve os três tool_result numa mensagem só.
 *
 * <p>Em config.properties, a05p.serial=true liga disable_parallel_tool_use (dentro do tool_choice):
 * no máximo uma tool por resposta. Compare as voltas e os tokens com a rodada normal.
 */
public class Paralelas {

    public static void executar() {
        AnthropicClient client = Claude.client();
        boolean serial = Config.get("a05p.serial", "false").equalsIgnoreCase("true");

        Opcoes o = Opcoes.de(List.of(BUSCAR_NOTA, BUSCAR_CLIENTE),
                "Das notas 1043, 1044 e 1051, quais foram emitidas e quais não? E de que clientes elas são?");
        if (serial) {
            o = o.comToolChoice(com.anthropic.models.messages.ToolChoice.ofAuto(
                    ToolChoiceAuto.builder().disableParallelToolUse(true).build()));
        }
        Resultado r = Loop.rodar(client, o);
        System.out.println("\n" + (serial ? "SERIAL" : "paralelo") + ": " + r.voltas() + " voltas para "
                + r.chamadas() + " chamadas de tool, " + r.entrada() + " tokens de entrada somando as voltas");
    }
}
