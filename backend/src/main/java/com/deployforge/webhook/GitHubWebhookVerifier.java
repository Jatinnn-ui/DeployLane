package com.deployforge.webhook;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.UnauthenticatedException;
import com.deployforge.config.DeployForgeProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Verifies the {@code X-Hub-Signature-256} header on incoming GitHub webhooks.
 *
 * <p>An unsigned or wrongly signed webhook is rejected outright - the endpoint is public and triggers
 * builds, so signature verification is the only thing standing between the internet and arbitrary
 * deployments.
 *
 * <p>Comparison uses {@link MessageDigest#isEqual} rather than {@code String.equals} to avoid leaking
 * information through timing.
 */
@Component
public class GitHubWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookVerifier.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final DeployForgeProperties properties;

    public GitHubWebhookVerifier(DeployForgeProperties properties) {
        this.properties = properties;
    }

    /**
     * @param rawBody the exact bytes GitHub sent; re-serialising the JSON would change the digest
     * @param signatureHeader value of {@code X-Hub-Signature-256}
     */
    public void verify(String rawBody, String signatureHeader) {
        String secret = properties.github().webhookSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BadRequestException(
                    ErrorCode.WEBHOOK_NOT_CONFIGURED,
                    "This server has no GITHUB_WEBHOOK_SECRET configured, so webhooks are rejected.");
        }
        if (!StringUtils.hasText(signatureHeader) || !signatureHeader.startsWith(PREFIX)) {
            throw new UnauthenticatedException(
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Missing X-Hub-Signature-256 header");
        }

        String expected = PREFIX + hexHmac(secret, rawBody);
        boolean matches =
                MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        signatureHeader.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            log.warn("webhook_signature_mismatch body_length={}", rawBody == null ? 0 : rawBody.length());
            throw new UnauthenticatedException(
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook signature verification failed");
        }
    }

    private String hexHmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is required but unavailable", e);
        }
    }
}
