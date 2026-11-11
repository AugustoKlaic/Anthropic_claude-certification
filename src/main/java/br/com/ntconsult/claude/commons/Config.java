package br.com.ntconsult.claude.commons;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Lê o arquivo src/main/resources/config.properties (chave da API, modelo etc.).
 * Valores em branco são tratados como "não definido".
 */
public final class Config {

    private static final Properties PROPS = carregar();

    private Config() {}

    public static Optional<String> get(String chave) {
        String valor = PROPS.getProperty(chave);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    public static String get(String chave, String padrao) {
        return get(chave).orElse(padrao);
    }

    public static String required(String chave) {
        return get(chave).orElseThrow(() -> new IllegalStateException(
                "Falta '" + chave + "' em src/main/resources/config.properties "
                        + "(copie config.properties para config.properties e preencha)."));
    }

    private static Properties carregar() {
        Properties props = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "config.properties não encontrado.");
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui ler config.properties", e);
        }
        return props;
    }
}
