package io.campuscore.restfulapi.common;

import java.util.Locale;

/**
 * Shared escaping for the bounded, case-insensitive substring filters behind the
 * admin list {@code search} query parameters.
 *
 * <p>The pattern is paired in SQL with {@code LIKE :search ESCAPE '\'}, so a term
 * containing {@code %} or {@code _} matches those characters literally instead of
 * widening into a wildcard. Lower-casing happens here once, which lets the query
 * compare against {@code LOWER(column)} without a second normalization step.
 */
public final class LikePattern {

    /**
     * Defensive ceiling for an operator-typed term. Anything longer is truncated
     * rather than rejected: an over-long term is a mistake, not an attack, and a
     * prefix search is still the closest honest answer.
     */
    public static final int MAX_TERM_LENGTH = 64;

    private LikePattern() {
    }

    /**
     * Returns the bound value for an optional {@code :search} parameter, or
     * {@code null} when no filter applies. Callers must skip the predicate
     * entirely on {@code null} so a request without {@code search} keeps the
     * exact SQL, ordering, and paging semantics it had before.
     */
    public static String contains(String term) {
        if (term == null) {
            return null;
        }
        String trimmed = term.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String bounded = trimmed.length() > MAX_TERM_LENGTH
                ? trimmed.substring(0, MAX_TERM_LENGTH)
                : trimmed;
        String escaped = bounded.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
