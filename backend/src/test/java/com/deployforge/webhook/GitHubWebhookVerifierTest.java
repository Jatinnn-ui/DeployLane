package com.deployforge.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deployforge.common.error.ApiException;
import com.deployforge.common.error.ErrorCode;
import com.deployforge.config.DeployForgeProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The webhook endpoint is public and triggers builds, so signature verification is the security control
 * that matters most in this codebase.
 */
class GitHubWebhookVerifierTest {

    private static final String SECRET = "a-webhook-secret-value";
    private static final String BODY = "{\"ref\":\"refs/heads/main\",\"deleted\":false}";

    private final GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(properties(SECRET));

    @Test
    @DisplayName("accepts a correctly signed payload")
    void acceptsValidSignature() {
        assertThatCode(() -> verifier.verify(BODY, "sha256=" + hmac(SECRET, BODY)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a signature produced with a different secret")
    void rejectsWrongSecret() {
        assertThatThrownBy(() -> verifier.verify(BODY, "sha256=" + hmac("other-secret", BODY)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("rejects a valid signature over a different body")
    void rejectsTamperedBody() {
        String signature = "sha256=" + hmac(SECRET, BODY);
        String tampered = BODY.replace("main", "attacker-branch");

        assertThatThrownBy(() -> verifier.verify(tampered, signature)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("rejects a missing or malformed header")
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> verifier.verify(BODY, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.verify(BODY, "")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.verify(BODY, hmac(SECRET, BODY)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.verify(BODY, "sha1=deadbeef")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("when no secret is configured every webhook is refused")
    void refusesWhenNotConfigured() {
        GitHubWebhookVerifier unconfigured = new GitHubWebhookVerifier(properties(""));

        assertThatThrownBy(() -> unconfigured.verify(BODY, "sha256=" + hmac(SECRET, BODY)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.WEBHOOK_NOT_CONFIGURED);
    }

    private static String hmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DeployForgeProperties properties(String webhookSecret) {
        return new DeployForgeProperties(
                "http://localhost:5173",
                "http://localhost:8080",
                new DeployForgeProperties.Security(
                        "x".repeat(48),
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        Duration.ofMinutes(10),
                        false,
                        "Lax"),
                new DeployForgeProperties.Github(
                        "client",
                        "secret",
                        webhookSecret,
                        "https://api.github.com",
                        "https://github.com/login/oauth/authorize",
                        "https://github.com/login/oauth/access_token",
                        "repo"),
                new DeployForgeProperties.Ai(
                        "heuristic",
                        Duration.ofSeconds(60),
                        200,
                        new DeployForgeProperties.Ai.Gemini("", "gemini-2.0-flash", "https://example.invalid"),
                        new DeployForgeProperties.Ai.OpenAi("", "gpt-4o-mini", "https://example.invalid")),
                new DeployForgeProperties.Deployment(
                        "./data/builds",
                        "localhost",
                        30000,
                        30999,
                        1024,
                        1.0,
                        256,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(3),
                        2,
                        "npipe:////./pipe/docker_engine",
                        "deployforge-apps",
                        "",
                        "/",
                        Duration.ofSeconds(90),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        24,
                        30,
                        72,
                        "local",
                        ""),
                new DeployForgeProperties.RateLimit(10, 6, 20, 120));
    }
}
