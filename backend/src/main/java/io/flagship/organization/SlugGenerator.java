package io.flagship.organization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

public final class SlugGenerator {

    private static final int MAX_LENGTH = 60;

    private SlugGenerator() {
    }

    /** "Acme Ltda. São Paulo" -> "acme-ltda-sao-paulo". */
    public static String slugify(String input) {
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            return "org";
        }
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }

    /** Returns the slug of the name, adding a short random suffix while it is already taken. */
    public static String unique(String name, Predicate<String> alreadyTaken) {
        String base = slugify(name);
        String candidate = base;
        while (alreadyTaken.test(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }
}
