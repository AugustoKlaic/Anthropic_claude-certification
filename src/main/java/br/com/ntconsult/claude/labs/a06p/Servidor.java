package br.com.ntconsult.claude.labs.a06p;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * O servidor MCP do Faturei, no transporte stdio: quem o inicia é o cliente (o Claude Code, o
 * Claude Desktop ou a classe {@link Cliente}), como subprocesso. Ele lê JSON-RPC do stdin e escreve
 * no stdout. Por isso este processo <b>nunca</b> escreve nada seu no stdout: log vai no stderr.
 *
 * <p>As três primitivas, e quem decide usar cada uma (camada 3 do desenho):
 * <ul>
 *   <li>tool: o modelo decide chamar
 *   <li>resource: a aplicação (ou a pessoa) decide anexar
 *   <li>prompt: a pessoa escolhe, como um comando
 * </ul>
 *
 * <p>Este é o main do servidor: ele não é chamado pela {@code Main} dos labs, e sim iniciado como
 * subprocesso. Variável de ambiente {@code QUEBRAR=1}: o passo "quebrar de propósito" (veja a tool buscar_nota).
 */
public class Servidor {

    public static void main(String[] args) throws InterruptedException {
        var transporte = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpServer.sync(transporte)
                .serverInfo("faturei", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(buscarNota(), buscarCliente())
                .resources(rejeicoes())
                .prompts(explicarRejeicao())
                .build();

        System.err.println("servidor do Faturei no ar (stdio)");
        // O servidor responde enquanto o stdin estiver aberto; o main só precisa ficar vivo.
        Thread.currentThread().join();
    }

    // ── Tools: o que o Claude vê é o de sempre (nome, descrição, schema). Mudou quem hospeda.
    private static SyncToolSpecification buscarNota() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("buscar_nota")
                        .title("Buscar nota fiscal")
                        .description("Busca uma nota fiscal do Faturei pelo número e devolve cliente_id, valor, data de emissão, "
                                + "status (emitida, rejeitada ou cancelada) e, se rejeitada, o código e o motivo. Use quando o "
                                + "usuário citar um número de nota. Não traz nome, plano nem situação do cliente: para isso, use "
                                + "buscar_cliente. Para o que fazer com um código de rejeição, leia o resource faturei://rejeicoes.")
                        .inputSchema(schema("numero", "Número da nota, com 4 dígitos. Ex.: 1043"))
                        .annotations(new ToolAnnotations(null, true, null, null, null, null))
                        .build())
                .callHandler((exchange, pedido) -> resultado(() -> {
                    String numero = String.valueOf(pedido.arguments().get("numero"));
                    // QUEBRAR=1: um aviso de progresso escrito no stdout. No stdio, o stdout é o canal do JSON-RPC,
                    // e este texto gruda no começo da resposta que sai logo depois: o cliente não consegue ler a
                    // resposta e fica esperando. Log de servidor stdio vai no stderr (System.err).
                    if ("1".equals(System.getenv("QUEBRAR"))) {
                        System.out.print("buscando a nota " + numero + "... ");
                        System.out.flush();
                    }
                    return Faturei.buscarNota(numero);
                }))
                .build();
    }

    private static SyncToolSpecification buscarCliente() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("buscar_cliente")
                        .title("Buscar cliente")
                        .description("Busca um cliente do Faturei pelo cliente_id e devolve nome, plano (Essencial ou Pro) e "
                                + "situação da conta. Use quando o usuário perguntar pelo plano ou pela situação de um cliente, "
                                + "ou depois de buscar_nota, para saber de quem é a nota.")
                        .inputSchema(schema("cliente_id", "Código do cliente no formato C-NNNNN. Ex.: C-31007"))
                        .annotations(new ToolAnnotations(null, true, null, null, null, null))
                        .build())
                .callHandler((exchange, pedido) -> resultado(() -> Faturei.buscarCliente(String.valueOf(pedido.arguments().get("cliente_id")))))
                .build();
    }

    // ── Resource: dado identificado por URI. No Claude Code: @faturei:faturei://rejeicoes
    private static SyncResourceSpecification rejeicoes() {
        var recurso = McpSchema.Resource.builder()
                .uri("faturei://rejeicoes")
                .name("rejeicoes")
                .title("Motivos de rejeição")
                .description("Tabela dos códigos de rejeição do Faturei e o que o cliente faz em cada um.")
                .mimeType("text/markdown")
                .build();
        return new SyncResourceSpecification(recurso, (exchange, pedido) -> new ReadResourceResult(
                List.of(new TextResourceContents(pedido.uri(), "text/markdown", Faturei.rejeicoes()))));
    }

    // ── Prompt: modelo de mensagem que a pessoa escolhe. No Claude Code: /mcp__faturei__explicar-rejeicao 1043
    private static SyncPromptSpecification explicarRejeicao() {
        var prompt = new McpSchema.Prompt("explicar-rejeicao",
                "Monta o pedido para explicar ao cliente por que uma nota foi rejeitada e o que fazer.",
                List.of(new PromptArgument("numero", "Número da nota", true)));
        return new SyncPromptSpecification(prompt, (exchange, pedido) -> {
            Object numero = pedido.arguments().get("numero");
            String texto = "Consulte a nota " + numero + " no Faturei e o cliente dela. Explique ao cliente, em até 4 frases "
                    + "e sem jargão, por que a nota foi rejeitada e o que ele faz agora, usando a tabela faturei://rejeicoes.";
            return new GetPromptResult(null, List.of(new PromptMessage(Role.USER, new TextContent(texto))));
        });
    }

    /**
     * Erro de execução: volta como resultado com isError, e o Claude pode se corrigir. (Erro de
     * protocolo, como tool que não existe, é outra coisa: volta como erro JSON-RPC.)
     */
    private static CallToolResult resultado(Supplier<String> fn) {
        try {
            return CallToolResult.builder().addTextContent(fn.get()).build();
        } catch (RuntimeException e) {
            return CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
        }
    }

    /** O inputSchema (JSON Schema) de uma tool com um único parâmetro string obrigatório. */
    private static Map<String, Object> schema(String parametro, String descricao) {
        return Map.of(
                "type", "object",
                "properties", Map.of(parametro, Map.of("type", "string", "description", descricao)),
                "required", List.of(parametro));
    }
}
