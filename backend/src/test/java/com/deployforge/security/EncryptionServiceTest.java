package com.deployforge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Secrets at rest are only as good as this class, so its guarantees are pinned down here. */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        encryptionService = EncryptionService.withRawKey(key);
    }

    @Test
    @DisplayName("round trips a value")
    void roundTrip() {
        String plaintext = "postgres://user:p4ssw0rd@db.internal:5432/app?sslmode=require";

        String encrypted = encryptionService.encrypt(plaintext);

        assertThat(encrypted).startsWith(EncryptionService.VERSION_PREFIX);
        assertThat(encrypted).doesNotContain("p4ssw0rd");
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("the same plaintext encrypts differently each time (fresh IV)")
    void usesRandomIv() {
        String first = encryptionService.encrypt("same-value");
        String second = encryptionService.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptionService.decrypt(first)).isEqualTo(encryptionService.decrypt(second));
    }

    @Test
    @DisplayName("tampering with the ciphertext is detected by the GCM tag")
    void detectsTampering() {
        String encrypted = encryptionService.encrypt("DATABASE_URL value");
        byte[] payload =
                Base64.getDecoder().decode(encrypted.substring(EncryptionService.VERSION_PREFIX.length()));
        payload[payload.length - 1] ^= 0x01;
        String tampered =
                EncryptionService.VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    @DisplayName("a value encrypted with another key cannot be read")
    void rejectsForeignKey() {
        String encrypted = encryptionService.encrypt("secret");
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);

        assertThatThrownBy(() -> EncryptionService.withRawKey(otherKey).decrypt(encrypted))
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    @DisplayName("an unversioned or truncated payload is rejected")
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> encryptionService.decrypt("not-encrypted"))
                .hasMessageContaining("unsupported format");
        assertThatThrownBy(() -> encryptionService.decrypt(EncryptionService.VERSION_PREFIX + "AAAA"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("null passes through, empty strings round trip")
    void handlesEdgeCases() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt(encryptionService.encrypt(""))).isEmpty();
    }

    @Test
    @DisplayName("only 32 byte keys are accepted")
    void rejectsWrongKeyLength() {
        assertThatThrownBy(() -> EncryptionService.withRawKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("generated keys are usable")
    void generatesUsableKey() {
        byte[] generated = Base64.getDecoder().decode(EncryptionService.generateKeyBase64());
        EncryptionService service = EncryptionService.withRawKey(generated);

        assertThat(service.decrypt(service.encrypt("value"))).isEqualTo("value");
    }
}
