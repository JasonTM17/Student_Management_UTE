package io.campuscore.restfulapi.engagement.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * RT-P2-5: vetted (Jsoup) HTML sanitizer applied to announcement bodies at the
 * write boundary. The frontend allowlist sanitizer stays the render-time
 * guarantee and the regex {@code ACTIVE_CONTENT} blacklist in
 * {@link AnnouncementWriteService} stays the hard reject; this layer closes
 * the gap for future read paths (mobile app, new endpoint) that forget the
 * render-time sanitizer.
 *
 * The policy mirrors {@code frontend/tests/fixtures/announcement-content-policy.json}
 * (RT-P2-2/RT-P2-5): {@code details}/{@code summary} survive so the editor's
 * accordion plugin round-trips, a restricted {@code id} survives so the anchor
 * plugin round-trips, and {@code iframe}/{@code video} (the editor's removed
 * {@code media} plugin) is dropped. {@code AnnouncementWriteServiceTest}
 * asserts this class against that fixture so the two hand-maintained policies
 * cannot drift apart silently.
 *
 * Deliberately applied on create/update writes ONLY — historical rows are not
 * rewritten (no undo exists), and they remain safe because every read path
 * already renders through the frontend sanitizer.
 */
public final class AnnouncementHtmlSanitizer {

    /** Sanitized body plus how many elements the policy removed (observability). */
    public record SanitizationResult(String html, int removedElements) {}

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "a", "abbr", "b", "blockquote", "br", "caption", "code", "col", "colgroup",
            "dd", "details", "div", "dl", "dt", "em", "figcaption", "figure", "h1", "h2",
            "h3", "h4", "h5", "h6", "hr", "i", "img", "li", "mark", "ol", "p", "pre",
            "s", "small", "span", "strong", "sub", "summary", "sup", "table", "tbody",
            "td", "tfoot", "th", "thead", "time", "tr", "u", "ul");

    private static final Set<String> GLOBAL_ATTRS = Set.of("class", "title", "dir", "lang", "align", "style");

    private static final Map<String, Set<String>> TAG_ATTRS = Map.of(
            "a", Set.of("href", "target", "rel"),
            "img", Set.of("src", "alt", "width", "height", "loading"),
            "td", Set.of("colspan", "rowspan", "scope", "headers"),
            "th", Set.of("colspan", "rowspan", "scope", "headers"),
            "ol", Set.of("start", "type"),
            "time", Set.of("datetime"),
            "col", Set.of("span", "width"),
            "colgroup", Set.of("span"));

    private static final Set<String> ID_ATTR_TAGS = Set.of(
            "article", "blockquote", "details", "div", "figcaption", "figure", "h1",
            "h2", "h3", "h4", "h5", "h6", "li", "ol", "p", "section", "summary",
            "table", "td", "th", "tr", "ul");

    private static final Pattern ID_VALUE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,127}$");

    /** Navigation targets only; {@code data:}/{@code javascript:} are absent by design. */
    private static final Pattern SAFE_URL =
            Pattern.compile("^(?:https?:|mailto:|tel:|#|/(?!/)|\\.{1,2}/)");
    /** {@code src} additionally permits the base64 image payloads the editor inlines. */
    private static final Pattern SAFE_IMAGE_URL = Pattern.compile(
            "^(?:https?:|data:image/(?:png|jpe?g|gif|webp|bmp);base64,[a-z0-9+/=\\s]+$|/(?!/)|\\.{1,2}/)");

    private static final Pattern UNSAFE_STYLE = Pattern.compile(
            "(?:expression\\s*\\(|javascript:|vbscript:|@import|behavior\\s*:|-moz-binding"
                    + "|url\\s*\\(\\s*['\"]?\\s*(?:javascript|vbscript|data\\s*:\\s*text/html))");

    /** Entities and control chars are normalized away before any scheme test. */
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-f]+);?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0020\\u00a0\\u2028\\u2029]");

    private static final Safelist POLICY = buildPolicy();

    private AnnouncementHtmlSanitizer() {}

    public static SanitizationResult sanitize(String html) {
        Document parsed = Jsoup.parse(html == null ? "" : html);
        int removed = countRemovedElements(parsed);
        String clean = Jsoup.clean(
                html == null ? "" : html,
                "",
                POLICY,
                new Document.OutputSettings().prettyPrint(false));
        return new SanitizationResult(clean, removed);
    }

    /** Elements the policy removed, so the write-time mutation is observable. */
    private static int countRemovedElements(Document parsed) {
        int removed = 0;
        for (Element element : parsed.getAllElements()) {
            String name = element.normalName();
            // Skip the parse wrappers (#root document node, html/head/body).
            if (name.equals("html") || name.equals("head") || name.equals("body")
                    || name.startsWith("#")) {
                continue;
            }
            if (!ALLOWED_TAGS.contains(name)) {
                removed++;
            }
        }
        return removed;
    }

    private static Safelist buildPolicy() {
        Safelist list = new PolicySafelist();
        list.addTags(ALLOWED_TAGS.toArray(String[]::new));
        list.addAttributes(":all", GLOBAL_ATTRS.toArray(String[]::new));
        TAG_ATTRS.forEach((tag, attrs) -> list.addAttributes(tag, attrs.toArray(String[]::new)));
        for (String tag : ID_ATTR_TAGS) {
            list.addAttributes(tag, "id");
        }
        return list;
    }

    /**
     * Scheme and value policies for the URL-bearing attributes are enforced
     * here (not via Jsoup protocol lists) so the logic mirrors the frontend's
     * entity/control-char-normalized checks exactly; Jsoup protocol prefixes
     * cannot express "data:image/* only" and would admit {@code data:text/html}.
     */
    private static final class PolicySafelist extends Safelist {
        @Override
        public boolean isSafeAttribute(String tagName, Element el, Attribute attr) {
            String name = attr.getKey().toLowerCase(Locale.ROOT);
            String value = attr.getValue() == null ? "" : attr.getValue();
            String normalized = normalizeForSchemeCheck(value);
            return switch (name) {
                case "href" -> SAFE_URL.matcher(normalized).find()
                        && super.isSafeAttribute(tagName, el, attr);
                case "src" -> SAFE_IMAGE_URL.matcher(normalized).find()
                        && super.isSafeAttribute(tagName, el, attr);
                case "style" -> !UNSAFE_STYLE.matcher(normalized).find()
                        && super.isSafeAttribute(tagName, el, attr);
                case "id" -> ID_ATTR_TAGS.contains(tagName) && ID_VALUE.matcher(value).matches();
                default -> super.isSafeAttribute(tagName, el, attr);
            };
        }
    }

    private static String normalizeForSchemeCheck(String value) {
        String decoded = NUMERIC_ENTITY.matcher(value).replaceAll(match -> {
            String digits = match.group(2);
            try {
                int code = match.group(1).equalsIgnoreCase("x")
                        ? Integer.parseInt(digits, 16)
                        : Integer.parseInt(digits, 10);
                return Character.isValidCodePoint(code) ? String.valueOf((char) code) : match.group(0);
            } catch (NumberFormatException exception) {
                return match.group(0);
            }
        });
        return CONTROL_CHARS.matcher(decoded).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Exposed for the shared-fixture parity test only. */
    public static Set<String> allowedTags() {
        return ALLOWED_TAGS;
    }

    public static Set<String> globalAttributes() {
        return GLOBAL_ATTRS;
    }

    public static Map<String, Set<String>> tagAttributes() {
        return TAG_ATTRS;
    }

    public static Set<String> idAttributeTags() {
        return ID_ATTR_TAGS;
    }
}
