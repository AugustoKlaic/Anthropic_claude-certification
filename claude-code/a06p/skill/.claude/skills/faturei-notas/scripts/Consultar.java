// O script da Skill: o Claude roda pelo Bash e só a saída entra no contexto, nunca este código.
// Roda direto, sem compilar: java Consultar.java nota 1043
// Num Faturei de verdade, isto chamaria a API REST do Faturei. Aqui os dados são os mesmos do
// servidor MCP (Faturei.java do projeto), copiados porque a Skill roda fora do projeto.
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Consultar {

    static final Map<String, String> NOTAS = Map.of(
            "1043", "{\"numero\":\"1043\",\"cliente_id\":\"C-31007\",\"valor\":1250.0,\"emitida_em\":\"2026-10-19\",\"status\":\"rejeitada\",\"motivo\":\"R-12: CFOP incompatível com destinatário de outro estado\"}",
            "1044", "{\"numero\":\"1044\",\"cliente_id\":\"C-31007\",\"valor\":1250.0,\"emitida_em\":\"2026-10-20\",\"status\":\"emitida\"}",
            "1051", "{\"numero\":\"1051\",\"cliente_id\":\"C-20417\",\"valor\":89.9,\"emitida_em\":\"2026-10-21\",\"status\":\"rejeitada\",\"motivo\":\"R-31: cliente com conta suspensa\"}");

    static final Map<String, String> CLIENTES = Map.of(
            "C-31007", "{\"cliente_id\":\"C-31007\",\"nome\":\"Padaria Estrela Ltda\",\"plano\":\"Pro\",\"situacao\":\"ativa\"}",
            "C-20417", "{\"cliente_id\":\"C-20417\",\"nome\":\"Ana Souza MEI\",\"plano\":\"Essencial\",\"situacao\":\"suspensa por falta de pagamento\"}");

    public static void main(String[] args) {
        // Acentos intactos mesmo quando o console do Windows não é UTF-8.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String tipo = args.length > 0 ? args[0] : "";
        String id = args.length > 1 ? args[1] : "";
        String saida;
        if (tipo.equals("nota")) {
            saida = NOTAS.get(id);
            if (saida == null) {
                erro("Nota " + id + " não encontrada. Os números do Faturei têm 4 dígitos e começam em 1000. Peça ao usuário para conferir o número no PDF da nota.");
            }
        } else if (tipo.equals("cliente")) {
            saida = CLIENTES.get(id);
            if (saida == null) {
                erro("Cliente " + id + " não encontrado. O código tem o formato C-NNNNN e vem no campo cliente_id da nota.");
            }
        } else {
            erro("Uso: Consultar.java nota <numero> | Consultar.java cliente <cliente_id>");
            return;
        }
        System.out.println(saida);
    }

    static void erro(String mensagem) {
        System.out.println(mensagem);
        System.out.flush();
        System.exit(1);
    }
}
