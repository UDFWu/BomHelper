package com.scsb.bomhelper.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * Small stand-alone utility for encrypting / decrypting property values with Jasypt.
 * <p>
 * The cipher configuration matches jasypt-spring-boot-starter 3.0.x defaults:
 * <ul>
 *   <li>Algorithm: PBEWITHHMACSHA512ANDAES_256</li>
 *   <li>Iterations: 1000</li>
 *   <li>Salt generator: RandomSaltGenerator</li>
 *   <li>IV generator: RandomIvGenerator</li>
 *   <li>Output: base64</li>
 * </ul>
 *
 * <h3>How to use</h3>
 *
 * <p><b>Encrypt a plain value</b> (run from IDE Run configuration or command line):
 * <pre>
 *   VM options:        -Djasypt.encryptor.password=YOUR_MASTER_KEY
 *   Program arguments: encrypt 1qaz@WSX3edc
 * </pre>
 * Output:
 * <pre>
 *   ENC(xxxxxxxxxxxxxxxxxxx)
 * </pre>
 * Paste the whole {@code ENC(...)} string into application-*.properties, e.g.:
 * <pre>
 *   spring.datasource.password=ENC(xxxxxxxxxxxxxxxxxxx)
 * </pre>
 *
 * <p><b>Decrypt (verify)</b>:
 * <pre>
 *   Program arguments: decrypt xxxxxxxxxxxxxxxxxxx
 * </pre>
 * (Do NOT include the outer {@code ENC(...)} wrapper when decrypting.)
 *
 * <p><b>Launch the Spring Boot app</b> with the same master key:
 * <pre>
 *   JASYPT_ENCRYPTOR_PASSWORD=YOUR_MASTER_KEY ./mvnw spring-boot:run
 *   # or
 *   java -Djasypt.encryptor.password=YOUR_MASTER_KEY -jar target/sbomHelper.war
 * </pre>
 * NEVER commit the master key to the repository. Keep it in environment variables,
 * CI/CD secrets, or your OS keychain.
 */
public class JasyptCli {

    public static void main(String[] args) {
        String master = resolveMasterKey();
        if (master == null || master.isBlank()) {
            System.err.println("[ERROR] Master key not provided.");
            System.err.println("        Set VM option: -Djasypt.encryptor.password=YOUR_KEY");
            System.err.println("        Or env var:    JASYPT_ENCRYPTOR_PASSWORD=YOUR_KEY");
            System.exit(1);
        }

        if (args.length < 2) {
            System.err.println("Usage: JasyptCli <encrypt|decrypt> <value>");
            System.err.println("Example: JasyptCli encrypt 1qaz@WSX3edc");
            System.exit(1);
        }

        String op = args[0];
        String value = args[1];

        PooledPBEStringEncryptor encryptor = buildEncryptor(master);

        try {
            switch (op.toLowerCase()) {
                case "encrypt" -> System.out.println("ENC(" + encryptor.encrypt(value) + ")");
                case "decrypt" -> System.out.println(encryptor.decrypt(value));
                default -> {
                    System.err.println("Unknown operation: " + op + " (expected encrypt or decrypt)");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Build an encryptor whose configuration matches jasypt-spring-boot-starter 3.0.x defaults,
     * so values encrypted here can be decrypted by the running Spring Boot application.
     */
    public static PooledPBEStringEncryptor buildEncryptor(String master) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(master);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }

    private static String resolveMasterKey() {
        String key = System.getProperty("jasypt.encryptor.password");
        if (key == null || key.isBlank()) {
            key = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        }
        return key;
    }
}
