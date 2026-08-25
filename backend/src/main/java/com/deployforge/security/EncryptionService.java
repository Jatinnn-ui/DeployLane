package com.deployforge.security;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.config.DeployForgeProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Authenticated encryption for secrets at rest (AES-256-GCM).
 *
 * <p>Applied to GitHub OAuth access tokens and environment variable values <em>before</em> they
 * reach PostgreSQL. A database dump on its own is therefore useless without the key.
 *
 * <p>Stored format: {@code v1:base64(iv || ciphertext || tag)} - the version prefix keeps future key
 * rotation / algorithm migration possible without ambiguity.
 */
@Service
public class EncryptionService {

    public static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * Explicitly annotated because the class declares a second, private constructor for
     * {@link #withRawKey(byte[])}. Spring only auto-selects a constructor when exactly one is declared,
     * so without this it would look for a no-arg constructor and fail at startup.
     */
    @Autowired
    public EncryptionService(DeployForgeProperties properties) {
        this(decodeKey(properties.security().encryptionKey()));
    }

    private EncryptionService(byte[] rawKey) {
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    /** Test / tooling entry point. */
    public static EncryptionService withRawKey(byte[] rawKey) {
        if (rawKey == null || rawKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES key must be exactly 32 bytes");
        }
        return new EncryptionService(rawKey);
    }

    public static String generateKeyBase64() {
        byte[] material = new byte[KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(material);
        return Base64.getEncoder().encodeToString(material);
    }

    private static byte[] decodeKey(String configured) {
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException(
                    """
                    ENCRYPTION_KEY is not configured.

                    DeployLane refuses to start without it because GitHub tokens and environment
                    variables are encrypted with it. Generate one with:

                        openssl rand -base64 32

                    then set ENCRYPTION_KEY in your .env file. Keep it safe: losing the key makes
                    every stored secret unrecoverable.""");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ENCRYPTION_KEY must be a base64 encoded 32 byte key", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY must decode to exactly 32 bytes (got " + decoded.length + ")");
        }
        return decoded;
    }

    /** Encrypts UTF-8 plaintext. Returns {@code null} for {@code null} input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            // Wrapped at a real boundary: the caller cannot recover, but must not see crypto internals.
            throw new com.deployforge.common.error.ApiException(
                    ErrorCode.ENCRYPTION_ERROR,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to encrypt value",
                    e);
        }
    }

    /** Decrypts a value produced by {@link #encrypt(String)}. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new BadRequestException(
                    ErrorCode.ENCRYPTION_ERROR, "Stored secret has an unsupported format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new BadRequestException(ErrorCode.ENCRYPTION_ERROR, "Stored secret is truncated");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext =
                    cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new com.deployforge.common.error.ApiException(
                    ErrorCode.ENCRYPTION_ERROR,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored secret could not be decrypted with the configured ENCRYPTION_KEY",
                    e);
        }
    }
}
