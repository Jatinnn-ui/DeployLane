package com.deployforge.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Redaction runs on every log line before storage, so both halves matter: exact known values and the
 * shapes of secrets DeployForge was never told about.
 */
class SecretRedactionServiceTest {

    private final SecretRedactionService redaction = new SecretRedactionService();

    @Test
    @DisplayName("removes known literal values")
    void redactsLiteralSecrets() {
        String log = "Connecting with token super-secret-token-value and key another-secret-1234";

        String result =
                redaction.redact(log, List.of("super-secret-token-value", "another-secret-1234"));

        assertThat(result).doesNotContain("super-secret-token-value");
        assertThat(result).doesNotContain("another-secret-1234");
        assertThat(result).contains(SecretRedactionService.PLACEHOLDER);
    }

    @Test
    @DisplayName("short values are ignored so ordinary logs stay readable")
    void ignoresShortLiterals() {
        String result = redaction.redact("Server listening on port 3000", List.of("3000"));

        assertThat(result).isEqualTo("Server listening on port 3000");
    }

    @Test
    @DisplayName("credentials inside a connection string are removed but the host is kept")
    void redactsConnectionStringPassword() {
        String result = redaction.redact("postgres://admin:hunter2000@db.internal:5432/app");

        assertThat(result).doesNotContain("hunter2000");
        assertThat(result).contains("db.internal:5432/app");
        assertThat(result).contains("admin");
    }

    @Test
    @DisplayName("provider token shapes are recognised without being configured")
    void redactsKnownTokenShapes() {
        assertThat(redaction.redact("token ghp_1234567890abcdefghijklmnopqrstuvwx"))
                .doesNotContain("ghp_1234567890abcdefghijklmnopqrstuvwx");
        assertThat(redaction.redact("key AIzaSyA1234567890abcdefghijklmnopqrstuvw"))
                .doesNotContain("AIzaSyA1234567890abcdefghijklmnopqrstuvw");
        assertThat(redaction.redact("openai sk-proj-abcdefghijklmnopqrstuvwxyz1234"))
                .doesNotContain("sk-proj-abcdefghijklmnopqrstuvwxyz1234");
        assertThat(redaction.redact("aws AKIAIOSFODNN7EXAMPLE")).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    @DisplayName("assignments with a sensitive looking name are redacted")
    void redactsSensitiveAssignments() {
        String result = redaction.redact("JWT_SECRET=please-do-not-print-me");

        assertThat(result).doesNotContain("please-do-not-print-me");
        assertThat(result).contains("JWT_SECRET");
    }

    @Test
    @DisplayName("private key blocks are removed entirely")
    void redactsPrivateKeyBlock() {
        String pem =
                "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA\n-----END RSA PRIVATE KEY-----";

        String result = redaction.redact("loaded key:\n" + pem);

        assertThat(result).doesNotContain("MIIEowIBAAKCAQEA");
        assertThat(result).contains(SecretRedactionService.PLACEHOLDER);
    }

    @Test
    @DisplayName("JWTs are removed")
    void redactsJwt() {
        String jwt =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnop";

        assertThat(redaction.redact("Authorization header " + jwt)).doesNotContain(jwt);
    }

    @Test
    @DisplayName("ordinary build output is left alone")
    void leavesNormalOutputIntact() {
        String log = "added 431 packages, and audited 432 packages in 12s";

        assertThat(redaction.redact(log)).isEqualTo(log);
    }

    @Test
    @DisplayName("null and empty input are safe")
    void handlesNullAndEmpty() {
        assertThat(redaction.redact(null)).isNull();
        assertThat(redaction.redact("")).isEmpty();
    }
}
