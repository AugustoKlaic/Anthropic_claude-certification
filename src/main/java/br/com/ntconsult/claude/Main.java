package br.com.ntconsult.claude;

import br.com.ntconsult.claude.labs.a01p.CincoVezes;
import br.com.ntconsult.claude.labs.a01p.ContarTokens;
import br.com.ntconsult.claude.labs.a01p.Thinking;
import br.com.ntconsult.claude.labs.a02p.ChamadaCrua;
import br.com.ntconsult.claude.labs.a02p.MultiTurno;
import br.com.ntconsult.claude.labs.a02p.ViaSdk;
import br.com.ntconsult.claude.labs.a03p.Cache;
import br.com.ntconsult.claude.labs.a03p.Lote;
import br.com.ntconsult.claude.labs.a03p.TresModelos;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Ponto de entrada único de todos os labs. Rode este main na IDE.
 *
 * <p>Deixe ativo só o que quer executar e comente o resto: cada passo faz chamadas reais
 * (e cobradas, exceto a contagem de tokens) na API.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        // ===== Lab a01p — Como o Claude responde =====
        passo("a01p - passo 1: contar tokens (gratuito)");
        ContarTokens.executar();

        passo("a01p - passo 2: a mesma chamada cinco vezes");
        CincoVezes.executar();

        passo("a01p - passo 3: thinking e effort");
        Thinking.executar();

        // ===== Lab a02p — Anatomia de uma requisição =====
        passo("a02p - passo 1a: a chamada crua (sem SDK)");
        ChamadaCrua.executar();

        passo("a02p - passo 1b: os eventos do streaming (sem SDK)");
        ChamadaCrua.executarStream();

        passo("a02p - passo 2: o mesmo pedido pelo SDK");
        ViaSdk.executar();

        passo("a02p - passo 3: conversa de três turnos montada à mão");
        MultiTurno.executar();

        // ===== Lab a03p — Escolher o modelo e pagar a conta =====
        passo("a03p - passo 3a: criar o lote (Batch API)");
        Lote.criar();
        passo("a03p - passo 3b: buscar os resultados do lote");
        Lote.buscar();

        passo("a03p - passo 1: o mesmo prompt nos três modelos");
        TresModelos.executar();

        passo("a03p - passo 2: prompt caching");
        Cache.executar();

    }

    private static void passo(String titulo) {
        System.out.println("\n##### " + titulo + " #####");
    }
}
