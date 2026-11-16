package br.com.ntconsult.claude.labs.a02p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import java.util.List;

/**
 * Passo 3 — uma conversa de três turnos montada na mão. A API não guarda nada:
 * quem guarda o histórico é o {@code historico} abaixo, e ele vai inteiro em toda chamada.
 *
 * <p>Com a02p.esquecer=true no config.properties, cada chamada leva só a pergunta da vez —
 * veja o que acontece com a resposta do turno 2.
 */
public class MultiTurno {

    private static final List<String> PERGUNTAS = List.of(
            "Meu serviço chama a API do Claude e às vezes recebe HTTP 529. Em uma frase: o que é?",
            "E se fosse 429?",
            "Qual dos dois o SDK já repete sozinho?");

    public static void executar() {
        AnthropicClient client = Claude.client();
        boolean esquecer = Config.get("a02p.esquecer", "false").equalsIgnoreCase("true");

        // Parâmetros que se repetem em todo turno; as mensagens mudam.
        MessageCreateParams.Builder historico = MessageCreateParams.builder()
                .model("claude-opus-5")
                .maxTokens(2000)
                .system("Responda em português, em uma ou duas frases.");

        for (int i = 0; i < PERGUNTAS.size(); i++) {
            String pergunta = PERGUNTAS.get(i);
            historico.addUserMessage(pergunta);

            MessageCreateParams params = esquecer
                    ? MessageCreateParams.builder()
                            .model("claude-opus-5")
                            .maxTokens(2000)
                            .system("Responda em português, em uma ou duas frases.")
                            .addUserMessage(pergunta)
                            .build()
                    : historico.build();

            Message r = client.messages().create(params);

            // O content inteiro volta para o histórico, e não só o texto: blocos thinking e tool_use
            // precisam voltar como vieram. addMessage(Message) faz exatamente isso.
            historico.addMessage(r);

            System.out.printf("%n=== turno %d · input_tokens=%d · output_tokens=%d%n",
                    i + 1, r.usage().inputTokens(), r.usage().outputTokens());
            System.out.println("> " + pergunta);
            System.out.println(Claude.texto(r));
        }
    }
}
