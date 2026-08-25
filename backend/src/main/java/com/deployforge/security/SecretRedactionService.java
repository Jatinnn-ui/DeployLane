package com.deployforge.security;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Removes secret material from text before it is persisted, displayed or sent to an AI provider.
 *
 * <p>Two complementary strategies:
 *
 * <ol>
 *   <li><b>Literal</b> - exact values we know are secret (decrypted environment variables, OAuth
 *       tokens) are replaced wherever they appear. This is the reliable half.
 *   <li><b>Heuristic</b> - well known shapes (connection strings with credentials, provider token
 *       prefixes, private key blocks, bearer tokens) are matched by pattern. This catches secrets a
 *       user's own application prints that DeployForge never knew about.
 * </ol>
 *
 * <p>This runs server side on ingest. Masking in the UI is presentation, not protection.
 */
@Service
public class SecretRedactionService {

    public static final String PLACEHOLDER = "[REDACTED_SECRET]";

    /** Literal values shorter than this are ignored to avoid mangling logs (e.g. PORT=3000). */
    private static final int MIN_LITERAL_LENGTH = 6;

    private static final List<Pattern> PATTERNS =
            List.of(
                    // scheme://user:password@host
                    Pattern.compile("(?i)\\b([a-z][a-z0-9+.\\-]{1,20}://)([^\\s:/@]+):([^\\s@/]+)@"),
                    // GitHub tokens
                    Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{16,255}\\b"),
                    Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{20,255}\\b"),
                    // Slack / Google / OpenAI / Anthropic style keys
                    Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
                    Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{30,}\\b"),
                    Pattern.compile("\\bsk-(?:proj-)?[A-Za-z0-9_\\-]{20,}\\b"),
                    Pattern.compile("\\bsk-ant-[A-Za-z0-9_\\-]{20,}\\b"),
                    // AWS
                    Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
                    // Bearer / Authorization headers
                    Pattern.compile("(?i)\\b(authorization\\s*[:=]\\s*)(bearer\\s+)?[A-Za-z0-9._\\-]{20,}"),
                    // JWT
                    Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\b"),
                    // PEM blocks
                    Pattern.compile(
                            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
                    // KEY=value where the key name looks sensitive
                    Pattern.compile(
                            "(?i)\\b([A-Z0-9_]*(?:SECRET|PASSWORD|PASSWD|TOKEN|API_?KEY|PRIVATE_?KEY|CREDENTIAL)[A-Z0-9_]*)\\s*[:=]\\s*(\"?)([^\\s\"']{4,})\\2"));

    /** Heuristic redaction only. */
    public String redact(String text) {
        return redact(text, List.of());
    }

    /** Literal + heuristic redaction. */
    public String redact(String text, Collection<String> literalSecrets) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;

        if (literalSecrets != null) {
            for (String secret : literalSecrets) {
                if (secret == null || secret.length() < MIN_LITERAL_LENGTH) {
                    continue;
                }
                result = result.replace(secret, PLACEHOLDER);
            }
        }

        // 1. scheme://user:password@host  ->  scheme://user:[REDACTED_SECRET]@host
        result = PATTERNS.get(0).matcher(result).replaceAll("$1$2:" + PLACEHOLDER + "@");

        for (int i = 1; i < PATTERNS.size(); i++) {
            Pattern pattern = PATTERNS.get(i);
            Matcher matcher = pattern.matcher(result);
            if (!matcher.find()) {
                continue;
            }
            matcher.reset();
            if (pattern.pattern().contains("SECRET|PASSWORD")) {
                result = matcher.replaceAll("$1=" + PLACEHOLDER);
            } else if (pattern.pattern().startsWith("(?i)\\b(authorization")) {
                result = matcher.replaceAll("$1" + PLACEHOLDER);
            } else {
                result = matcher.replaceAll(PLACEHOLDER);
            }
        }
        return result;
    }

    /** True when the text still looks like it carries credentials after redaction. */
    public boolean containsLikelySecret(String text) {
        if (text == null) {
            return false;
        }
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
