package pt.vclubroom.club.domain;

import java.text.Normalizer;
import java.util.Locale;

import pt.vclubroom.shared.error.InvalidRequestException;

/** URL-safe club identifier (BR-01): 3–40 characters, lowercase letters, digits and inner hyphens. */
public record Slug(String value) {

    private static final String PATTERN = "[a-z0-9](?:[a-z0-9-]{1,38})[a-z0-9]";

    public Slug {
        if (value == null || !value.matches(PATTERN)) {
            throw new InvalidRequestException("INVALID_SLUG", "Slug must match " + PATTERN);
        }
    }

    /** "Échecs & Go!" → "echecs-go"; very short names get a "-club" suffix. */
    public static Slug fromName(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        if (slug.length() < 3) {
            slug = (slug + "-club").replaceAll("^-+", "");
        }
        return new Slug(slug);
    }

    /** "chess-club" → "chess-club-2", keeping the result within 40 characters. */
    public Slug withSuffix(int n) {
        String base = value.length() > 36 ? value.substring(0, 36).replaceAll("-+$", "") : value;
        return new Slug(base + "-" + n);
    }
}