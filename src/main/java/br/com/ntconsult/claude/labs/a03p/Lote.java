package br.com.ntconsult.claude.labs.a03p;

import br.com.ntconsult.claude.commons.Claude;
import br.com.ntconsult.claude.commons.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchRequestCounts;
import com.anthropic.models.messages.batches.MessageBatchResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Passo 3 — Batch API. Metade do preço, em troca de a resposta vir quando der: a maioria dos
 * lotes termina em menos de 1 h, e o limite é 24 h. Por isso são dois métodos:
 *
 * <ul>
 *   <li>{@link #criar()} cria o lote e guarda o id em lote.txt (na raiz do projeto)
 *   <li>{@link #buscar()} consulta o status e, se terminou, mostra os resultados. Usa o id de
 *       a03p.lote-id (config.properties), se existir; senão o do lote.txt
 * </ul>
 */
public class Lote {

    private static final String MODELO = "claude-haiku-4-5-20251001";
    private static final Path ARQUIVO_ID = Path.of("lote.txt");

    private static final List<String> TICKETS = List.of(
            "Não consigo entrar: a tela de login volta para ela mesma depois da senha.",
            "A fatura de setembro veio cobrada duas vezes no cartão.",
            "Seria ótimo poder exportar o relatório em CSV.",
            "O app fecha sozinho quando abro a aba de pedidos no Android.",
            "Quero trocar o e-mail da conta da empresa.",
            "O sistema está muito lento desde ontem à tarde.");

    public static void criar() throws IOException {
        AnthropicClient client = Claude.client();

        BatchCreateParams.Builder params = BatchCreateParams.builder();
        for (int i = 0; i < TICKETS.size(); i++) {
            params.addRequest(BatchCreateParams.Request.builder()
                    .customId("ticket-" + (i + 1))
                    .params(BatchCreateParams.Request.Params.builder()
                            .model(MODELO)
                            .maxTokens(50)
                            .system("Classifique o ticket de suporte numa palavra só: "
                                    + "acesso, cobranca, bug, pedido ou desempenho.")
                            .addUserMessage(TICKETS.get(i))
                            .build())
                    .build());
        }
        MessageBatch lote = client.messages().batches().create(params.build());

        Files.writeString(ARQUIVO_ID, lote.id());
        System.out.println("lote criado: " + lote.id() + " (guardado em lote.txt)");
        System.out.println("status: " + lote.processingStatus().asString() + " · " + contagem(lote.requestCounts()));
        System.out.println("expira em: " + lote.expiresAt());
    }

    public static void buscar() throws IOException {
        AnthropicClient client = Claude.client();
        String id = Config.get("a03p.lote-id").orElseGet(Lote::idGuardado);

        MessageBatch lote = client.messages().batches().retrieve(id);
        System.out.println("lote " + id + ": " + lote.processingStatus().asString()
                + " · " + contagem(lote.requestCounts()));
        if (!lote.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)) {
            System.out.println("Ainda não terminou. Rode de novo daqui a pouco — no app de verdade, isso é polling.");
            return;
        }

        // Os resultados podem vir em qualquer ordem: quem liga resultado a pedido é o custom_id.
        double total = 0;
        try (StreamResponse<MessageBatchIndividualResponse> resultados =
                client.messages().batches().resultsStreaming(id)) {
            for (MessageBatchIndividualResponse item : (Iterable<MessageBatchIndividualResponse>) resultados.stream()::iterator) {
                MessageBatchResult r = item.result();
                if (!r.isSucceeded()) {
                    System.out.println(item.customId() + ": " + tipo(r));
                    continue;
                }
                var mensagem = r.asSucceeded().message();
                total += Precos.custo(MODELO, mensagem.usage(), 0.5);
                int n = Integer.parseInt(item.customId().split("-")[1]);
                System.out.printf("%s: %-12s ← %s%n", item.customId(), Claude.texto(mensagem).strip(), TICKETS.get(n - 1));
            }
        }
        System.out.println("\ncusto no lote: " + Precos.dolar(total) + " · em tempo real seria " + Precos.dolar(total * 2));
    }

    private static String idGuardado() {
        try {
            return Files.readString(ARQUIVO_ID).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Sem lote.txt: rode Lote.criar() antes, ou defina a03p.lote-id.", e);
        }
    }

    private static String contagem(MessageBatchRequestCounts c) {
        return "{processing=" + c.processing() + ", succeeded=" + c.succeeded() + ", errored=" + c.errored()
                + ", canceled=" + c.canceled() + ", expired=" + c.expired() + "}";
    }

    private static String tipo(MessageBatchResult r) {
        if (r.isErrored()) return "errored";
        if (r.isCanceled()) return "canceled";
        if (r.isExpired()) return "expired";
        return "outro";
    }
}
