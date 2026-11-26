package br.com.ntconsult.claude.labs.a06p;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * O lado do cliente, sem modelo nenhum: é o que o Claude Code faz por baixo quando conecta um servidor
 * MCP. Inicia o {@link Servidor} como subprocesso (stdio), descobre o que ele oferece e usa cada
 * primitiva. Cada linha "→" é uma mensagem JSON-RPC que sai pelo stdin do servidor.
 */
public class Cliente {

    /** Passo 1: o protocolo por baixo. Não chama modelo, não precisa de chave. */
    public static void executar() {
        try (McpSyncClient cliente = conectar(false)) {
            System.out.println("revisão do protocolo: " + cliente.getCurrentInitializationResult().protocolVersion());
            System.out.println("servidor: " + cliente.getServerInfo().name() + " " + cliente.getServerInfo().version());

            System.out.println("\no que o servidor oferece");
            System.out.println("  (cada listagem é uma mensagem: tools/list, resources/list, prompts/list)");
            for (var t : cliente.listTools().tools()) {
                var propriedades = (Map<?, ?>) t.inputSchema().get("properties");
                System.out.println("  tool      " + t.name() + "  (schema: " + String.join(", ", propriedades.keySet().stream().map(String::valueOf).toList()) + ")");
            }
            for (var r : cliente.listResources().resources()) {
                System.out.println("  resource  " + r.uri());
            }
            for (var p : cliente.listPrompts().prompts()) {
                var args = p.arguments().stream().map(McpSchema.PromptArgument::name).toList();
                System.out.println("  prompt    " + p.name() + "  (argumentos: " + String.join(", ", args) + ")");
            }

            System.out.println("\ntool: o modelo decidiria chamar");
            mostrar(cliente.callTool(new CallToolRequest("buscar_nota", Map.of("numero", "1043"))));

            System.out.println("\ntool com erro de execução: volta como resultado, e o modelo lê a mensagem");
            mostrar(cliente.callTool(new CallToolRequest("buscar_nota", Map.of("numero", "9999"))));

            System.out.println("\ntool que não existe: erro de protocolo (JSON-RPC), não resultado");
            try {
                mostrar(cliente.callTool(new CallToolRequest("cancelar_nota", Map.of("numero", "1043"))));
            } catch (RuntimeException e) {
                System.out.println("  lançou: " + e.getMessage());
            }

            System.out.println("\nresource: a aplicação (ou a pessoa) decide anexar");
            var tabela = cliente.readResource(new McpSchema.ReadResourceRequest("faturei://rejeicoes"));
            var conteudo = (McpSchema.TextResourceContents) tabela.contents().get(0);
            String[] linhas = conteudo.text().split("\n");
            for (int i = 0; i < Math.min(5, linhas.length); i++) {
                System.out.println("  " + linhas[i]);
            }
            System.out.println("  …");

            System.out.println("\nprompt: a pessoa escolhe, como um comando");
            var pedido = cliente.getPrompt(new McpSchema.GetPromptRequest("explicar-rejeicao", Map.of("numero", "1043")));
            for (var m : pedido.messages()) {
                String texto = m.content() instanceof TextContent t ? t.text() : "[" + m.content().type() + "]";
                System.out.println("  " + m.role().name().toLowerCase() + ": " + texto);
            }
        }
    }

    /**
     * Passo 1, parte "quebrar de propósito": o servidor sobe com QUEBRAR=1 e escreve um aviso no stdout,
     * que é o canal do JSON-RPC. O aviso gruda no começo da resposta, o cliente não consegue ler e desiste
     * por timeout.
     */
    public static void quebrar() {
        try (McpSyncClient cliente = conectar(true)) {
            System.out.println("tool: o modelo decidiria chamar (com o servidor escrevendo no stdout)");
            mostrar(cliente.callTool(new CallToolRequest("buscar_nota", Map.of("numero", "1043"))));
        } catch (RuntimeException e) {
            System.out.println("  ✘ " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  a resposta chegou colada no texto que o servidor escreveu no stdout.");
            System.out.println("  Log de servidor stdio vai no stderr (System.err).");
        }
    }

    // ---------------------------------------------------------------- apoio

    /** Inicia o servidor como subprocesso e faz o handshake. O print "→" mostra cada mensagem que sai. */
    private static McpSyncClient conectar(boolean quebrar) {
        ServerParameters.Builder servidor = ServerParameters.builder(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java")
                // Mesmo classpath do processo atual: o servidor é uma classe deste projeto.
                .args("-cp", System.getProperty("java.class.path"), Servidor.class.getName());
        if (quebrar) {
            servidor.addEnvVar("QUEBRAR", "1");
        }
        var transporte = new StdioClientTransport(servidor.build(), McpJsonDefaults.getMapper()) {
            @Override
            public Mono<Void> sendMessage(McpSchema.JSONRPCMessage mensagem) {
                if (mensagem instanceof McpSchema.JSONRPCRequest r) {
                    System.out.println("  → " + r.method());
                } else if (mensagem instanceof McpSchema.JSONRPCNotification n) {
                    System.out.println("  → " + n.method() + " (notificação)");
                }
                return super.sendMessage(mensagem);
            }
        };
        // O stderr do servidor é onde ele loga; aqui fica de fora para não poluir a saída.
        transporte.setStdErrorHandler(linha -> { });

        System.out.println("conectando");
        McpSyncClient cliente = McpClient.sync(transporte)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(15))
                .build();
        cliente.initialize();
        return cliente;
    }

    private static void mostrar(CallToolResult r) {
        String texto = r.content().stream()
                .map(c -> c instanceof TextContent t ? t.text() : "[" + c.type() + "]")
                .reduce("", String::concat);
        System.out.println("  isError: " + (r.isError() != null && r.isError()) + "  " + texto);
    }
}
