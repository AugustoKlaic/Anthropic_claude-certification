package br.com.ntconsult.claude.labs.a05p;

import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_CLIENTE;
import static br.com.ntconsult.claude.labs.a05p.Faturei.BUSCAR_NOTA;
import static br.com.ntconsult.claude.labs.a05p.Faturei.CANCELAR_NOTA;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.StopReason;
import java.util.List;
import java.util.Scanner;

/**
 * Passo 4 — humano no meio. Cancelar nota não tem volta: o loop pergunta antes de executar.
 * Em config.properties, a05p.aprovar=s ou n responde sem perguntar; em branco, a pergunta aparece
 * no console da IDE (digite s ou n e Enter).
 */
public class Aprovacao {

    public static void executar() {
        AnthropicClient client = Claude.client();
        var r = Loop.rodar(client, Loop.Opcoes.de(
                        List.of(BUSCAR_NOTA, BUSCAR_CLIENTE, CANCELAR_NOTA),
                        "A nota 1045 saiu em duplicidade com a 1044. Pode cancelar a 1045?")
                .comAprovacao(b -> {
                    System.out.println("\n   ⚠ o Claude quer executar " + b.name() + " " + b._input());
                    String resposta = Config.get("a05p.aprovar", "");
                    if (resposta.isBlank()) {
                        System.out.print("   Aprovar? (s/n) ");
                        Scanner teclado = new Scanner(System.in);
                        resposta = teclado.hasNextLine() ? teclado.nextLine() : "n";
                    }
                    boolean sim = resposta.strip().equalsIgnoreCase("s");
                    System.out.println("   " + (sim ? "✔ aprovado" : "✘ negado"));
                    return sim;
                }));
        System.out.println("\nstop_reason final: " + r.resposta().stopReason().map(StopReason::asString).orElse("null"));
    }
}
