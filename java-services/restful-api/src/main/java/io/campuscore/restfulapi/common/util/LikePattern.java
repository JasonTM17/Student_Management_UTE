package io.campuscore.restfulapi.common.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared escaping for the bounded, case-insensitive substring filters behind the
 * admin list {@code search} query parameters.
 *
 * <p>The pattern is paired in SQL with {@code LIKE :search ESCAPE '\'}, so a term
 * containing {@code %} or {@code _} matches those characters literally instead of
 * widening into a wildcard. Lower-casing happens here once, which lets the query
 * compare against {@code LOWER(column)} without a second normalization step.
 *
 * <p>The pattern is also diacritic-folded (Nguyễn matches "nguyen"): Vietnamese
 * operators routinely type without tone marks, and a search that only matches the
 * exact accented spelling returns an empty page for most natural queries. The
 * bound value is folded here with NFD normalization; the column side is folded in
 * SQL with the {@link #FOLD_FROM}/{@link #FOLD_TO} {@code TRANSLATE} pair, which
 * exists in both PostgreSQL and H2, so no extension or migration is required.
 */
public final class LikePattern {

    /**
     * Defensive ceiling for an operator-typed term. Anything longer is truncated
     * rather than rejected: an over-long term is a mistake, not an attack, and a
     * prefix search is still the closest honest answer.
     */
    public static final int MAX_TERM_LENGTH = 64;
    public static final int MAX_TOKENS = 8;

    private static final Set<String> HONORIFIC_TOKENS = Set.of(
            "gs", "pgs", "ts", "ths", "ks", "cn", "gv",
            "gsts", "pgsts", "thsks", "dr", "prof");

    /**
     * Vietnamese lowercase diacritic characters folded to their ASCII base
     * letter by {@link #FOLD_TO}. Uppercase forms never appear here because the
     * column side applies {@code LOWER(...)} before the translate, mirroring the
     * {@code Locale.ROOT} lower-case applied to the bound value. The pair is
     * generated from the group table below, so {@code TRANSLATE}'s positional
     * pairing can never drift out of alignment.
     */
    public static final String FOLD_FROM;
    public static final String FOLD_TO;

    private static final String[] FOLD_BASE_LETTERS = {"a", "e", "i", "o", "u", "y", "d"};
    private static final String[] FOLD_MARKED_GROUPS = {
            "áàảãạăằắẳẵặâầấẩẫậ",
            "éèẻẽẹêềếểễệ",
            "íìỉĩị",
            "óòỏõọôồốổỗộơờớởỡợ",
            "úùủũụưừứửữự",
            "ýỳỷỹỵ",
            "đ",
    };

    static {
        StringBuilder from = new StringBuilder();
        StringBuilder to = new StringBuilder();
        for (int group = 0; group < FOLD_BASE_LETTERS.length; group++) {
            for (char marked : FOLD_MARKED_GROUPS[group].toCharArray()) {
                from.append(marked);
                to.append(FOLD_BASE_LETTERS[group]);
            }
        }
        FOLD_FROM = from.toString();
        FOLD_TO = to.toString();
    }

    private LikePattern() {
    }

    /**
     * Folds Vietnamese diacritics and tone marks to their ASCII base letters
     * ("Triết" → "triet"). Used on the bound pattern value; the column side is
     * folded with the {@code TRANSLATE} pair in SQL.
     */
    public static String fold(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(decomposed.length());
        for (char c : decomposed.toCharArray()) {
            out.append(c == 'đ' ? 'd' : c == 'Đ' ? 'D' : c);
        }
        return out.toString();
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
        String escaped = fold(bounded.toLowerCase(Locale.ROOT))
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Splits an operator query into diacritic-folded, LIKE-escaped substring
     * tokens ({@code "%token%"}) so multi-word searches such as
     * {@code "Hoàng Thái Xuân Khoa"} or {@code "Trần Đình Khôi"} match across
     * {@code firstName}/{@code lastName}/{@code email}/identifier columns even
     * when the stored record splits surname, academic title, and given name
     * across separate columns.
     *
     * <p>When a multi-word query includes Vietnamese academic honorifics
     * ({@code GS.TS.}, {@code PGS.TS.}, {@code TS.}, {@code ThS.}), those
     * prefixes are stripped so searching {@code "ThS. Trần Văn Bình"} still
     * matches a user row whose honorific is stored on the Lecturer profile
     * rather than in {@code User.lastName}. A single-token honorific query
     * (e.g. {@code "PGS.TS."}) is preserved verbatim.
     */
    public static List<String> tokens(String term) {
        if (term == null) {
            return List.of();
        }
        String trimmed = term.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String bounded = trimmed.length() > MAX_TERM_LENGTH
                ? trimmed.substring(0, MAX_TERM_LENGTH)
                : trimmed;
        String[] rawParts = bounded.split("\\s+");
        List<String> nonHonorific = new ArrayList<>(rawParts.length);
        List<String> allTokens = new ArrayList<>(rawParts.length);
        for (String raw : rawParts) {
            if (raw.isEmpty()) {
                continue;
            }
            String pattern = contains(raw);
            if (pattern == null) {
                continue;
            }
            allTokens.add(pattern);
            String foldedAlpha = fold(raw.toLowerCase(Locale.ROOT)).replace(".", "");
            if (!HONORIFIC_TOKENS.contains(foldedAlpha)) {
                nonHonorific.add(pattern);
            }
            if (allTokens.size() >= MAX_TOKENS) {
                break;
            }
        }
        return nonHonorific.isEmpty() ? allTokens : nonHonorific;
    }

    /**
     * Extracts individual {@code "%token%"} patterns from an already-wrapped
     * {@link #contains(String)} pattern. Preserves single-token patterns byte
     * for byte while allowing multi-word patterns to match across columns.
     */
    public static List<String> tokensFromContainsPattern(String containsPattern) {
        if (containsPattern == null || containsPattern.isBlank()) {
            return List.of();
        }
        String inner = containsPattern;
        if (inner.startsWith("%") && inner.endsWith("%") && inner.length() >= 2) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        if (inner.isEmpty()) {
            return List.of();
        }
        if (!inner.contains(" ")) {
            return List.of("%" + inner + "%");
        }
        String[] parts = inner.split("\\s+");
        List<String> nonHonorific = new ArrayList<>(parts.length);
        List<String> allTokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            String tokenPattern = "%" + part + "%";
            allTokens.add(tokenPattern);
            String normalized = part.replace("\\", "").replace(".", "");
            if (!HONORIFIC_TOKENS.contains(normalized)) {
                nonHonorific.add(tokenPattern);
            }
            if (allTokens.size() >= MAX_TOKENS) {
                break;
            }
        }
        return nonHonorific.isEmpty() ? allTokens : nonHonorific;
    }
}
