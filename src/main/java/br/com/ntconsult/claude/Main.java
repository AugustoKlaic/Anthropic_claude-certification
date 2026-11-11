package br.com.ntconsult.claude;

import br.com.ntconsult.claude.labs.a01p.CincoVezes;
import br.com.ntconsult.claude.labs.a01p.ContarTokens;
import br.com.ntconsult.claude.labs.a01p.Thinking;

/**
 * Lab da aula 1 — Como o Claude responde.
 *
 * <p>Rode este main na IDE. Para executar só um passo, comente os outros.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("##### Passo 1: contar tokens (gratuito) #####");
        ContarTokens.executar();

        System.out.println("\n##### Passo 2: a mesma chamada cinco vezes #####");
        CincoVezes.executar();

        System.out.println("\n##### Passo 3: thinking e effort #####");
        Thinking.executar();
    }
}
