package com.deployforge.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Slug generation and validation.
 *
 * <p>Slugs end up in Docker container names, image tags and hostnames, so they are restricted to a
 * conservative character set: lowercase alphanumerics separated by single hyphens.
 */
public final class Slugs {

    public static final Pattern VALID = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private Slugs() {}

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        String slug = NON_ALNUM.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        if (slug.length() > 63) {
            slug = EDGE_HYPHENS.matcher(slug.substring(0, 63)).replaceAll("");
        }
        return slug;
    }

    public static boolean isValid(String slug) {
        return slug != null && VALID.matcher(slug).matches();
    }
}
