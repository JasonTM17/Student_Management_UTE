package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.service.AnnouncementHtmlSanitizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * RT-P2-5 proving tests: every payload from the frontend XSS set must come out
 * of the write-time sanitizer neutralized, the plugins the editor keeps
 * enabled must round-trip (RT-P2-2), and the whole policy must match the
 * shared allowlist fixture that the TypeScript suite also asserts against, so
 * the two hand-maintained policies cannot drift apart silently.
 */
class AnnouncementHtmlSanitizerTest {

    private static Path sharedFixture() {
        // Repo layout: java-services/restful-api is the module cwd under Maven.
        // Walk up until the shared frontend fixture is found so aggregate
        // builds run from any directory still resolve the single source of truth.
        for (Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
                dir != null;
                dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get(
                    "frontend", "tests", "fixtures", "announcement-content-policy.json"));
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Shared allowlist fixture frontend/tests/fixtures/announcement-content-policy.json"
                        + " not found above " + System.getProperty("user.dir"));
    }

    private static JsonNode fixture() throws IOException {
        return new ObjectMapper().readTree(Files.readAllBytes(sharedFixture()));
    }

    @Test
    void storedXssPayloadsAreNeutralizedByTheWriteTimeSanitizer() {
        // Each payload mirrors the frontend suite (tests/rich-editor.test.js),
        // including the markdown-branch bypass set (RT-P1-1): interior
        // tab/newline, entities, mixed case and leading whitespace all
        // normalize to a rejected javascript: scheme.
        record Attack(String payload, String forbidden) {}
        List<Attack> attacks = List.of(
                new Attack("<a href=\"java\tscript:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\"java\nscript:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\"java\rscript:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\"JAVASCRIPT:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\"jav&#x61;script:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\" javascript:alert(1)\">x</a>", "(?i)javascript\\s*:"),
                new Attack("<a href=\"vbscript:msgbox(1)\">x</a>", "(?i)vbscript\\s*:"),
                new Attack("<a href=\"data:text/html,<b>x</b>\">x</a>", "(?i)data\\s*:\\s*text/html"),
                new Attack("<img src=\"data:text/html;base64,PHNjcmlwdD4=\">", "(?i)data\\s*:\\s*text/html"),
                new Attack("<div onclick=\"alert(1)\">x</div>", "(?i)onclick\\s*="),
                new Attack("<details open ontoggle=alert(1)><summary>s</summary></details>", "(?i)ontoggle\\s*="),
                new Attack("<iframe src=\"https://evil.example\"></iframe>", "(?i)<iframe"),
                new Attack("<svg onload=alert(1)></svg>", "(?i)<svg|onload\\s*="),
                new Attack("<p id=\"9bad id\">x</p>", "id=\"9bad id\""));

        for (Attack attack : attacks) {
            AnnouncementHtmlSanitizer.SanitizationResult result =
                    AnnouncementHtmlSanitizer.sanitize(attack.payload());
            assertFalse(
                    Pattern.compile(attack.forbidden()).matcher(result.html()).find(),
                    () -> "payload survived sanitizing: " + attack.payload() + " -> " + result.html());
        }
    }

    @Test
    void pluginsKeptByTheEditorRoundTripThroughTheSanitizer() {
        // RT-P2-2: accordion and anchor are the plugins the editor keeps, so
        // their output must survive the write-time policy.
        AnnouncementHtmlSanitizer.SanitizationResult accordion = AnnouncementHtmlSanitizer.sanitize(
                "<details><summary>Tiêu đề mục</summary><p>nội dung bên trong</p></details>");
        assertTrue(accordion.html().contains("<details>"), accordion.html());
        assertTrue(accordion.html().contains("<summary>"), accordion.html());
        assertTrue(accordion.html().contains("nội dung bên trong"), accordion.html());

        AnnouncementHtmlSanitizer.SanitizationResult anchor = AnnouncementHtmlSanitizer.sanitize(
                "<h2 id=\"muc-1\">Mục 1</h2><p><a href=\"#muc-1\">lên đầu mục</a></p>");
        assertTrue(anchor.html().contains("id=\"muc-1\""), anchor.html());
        assertTrue(anchor.html().contains("href=\"#muc-1\""), anchor.html());

        // RT-P2-2: the media plugin was removed — its iframe output must not survive.
        AnnouncementHtmlSanitizer.SanitizationResult media = AnnouncementHtmlSanitizer.sanitize(
                "<iframe src=\"https://youtu.be/x\"></iframe>");
        assertFalse(media.html().toLowerCase(Locale.ROOT).contains("iframe"), media.html());
    }

    @Test
    void policyMatchesTheSharedAllowlistFixture() throws IOException {
        JsonNode policy = fixture();
        Set<String> fixtureTags = new HashSet<>();
        policy.get("allowedTags").forEach(tag -> fixtureTags.add(tag.asText()));
        assertEquals(fixtureTags, AnnouncementHtmlSanitizer.allowedTags(),
                "ALLOWED_TAGS drifted from the shared fixture " + sharedFixture());

        Set<String> fixtureGlobals = new HashSet<>();
        policy.get("globalAttributes").forEach(attr -> fixtureGlobals.add(attr.asText()));
        assertEquals(fixtureGlobals, AnnouncementHtmlSanitizer.globalAttributes());

        assertEquals(fixtureTags, new HashSet<>(AnnouncementHtmlSanitizer.allowedTags()));

        JsonNode tagAttributes = policy.get("tagAttributes");
        assertEquals(tagAttributes.size(), AnnouncementHtmlSanitizer.tagAttributes().size(),
                "tagAttributes drifted from the shared fixture");
        tagAttributes.fieldNames().forEachRemaining(tag -> assertEquals(
                stringSet(tagAttributes.get(tag)),
                AnnouncementHtmlSanitizer.tagAttributes().get(tag),
                "attributes for <" + tag + "> drifted from the shared fixture"));

        Set<String> fixtureIdTags = new HashSet<>();
        policy.get("idAttributeTags").forEach(tag -> fixtureIdTags.add(tag.asText()));
        assertEquals(fixtureIdTags, AnnouncementHtmlSanitizer.idAttributeTags());

        // The id value policy and the unsafe-style screen must be the same
        // regexes the fixture enumerates.
        Pattern.compile(policy.get("idValuePattern").asText());
        Pattern.compile(policy.get("unsafeStylePattern").asText());
        assertTrue(Pattern.compile(policy.get("idValuePattern").asText()).matcher("muc-1").matches());
        assertFalse(Pattern.compile(policy.get("idValuePattern").asText()).matcher("9bad").matches());

        // Editor limits: the fixture pins the server cap the client derives from.
        assertEquals(200_000, policy.get("editorLimits").get("maxContentChars").asInt());

        // Plugins the two ends agree on.
        Set<String> pluginsRemoved = new HashSet<>();
        policy.get("pluginsRemoved").forEach(plugin -> pluginsRemoved.add(plugin.asText()));
        assertEquals(Set.of("media"), pluginsRemoved);
    }

    @Test
    void sanitizedOutputDiffersFromRawInputAndReportsRemovedElements() {
        AnnouncementHtmlSanitizer.SanitizationResult result = AnnouncementHtmlSanitizer.sanitize(
                "<p>nội dung</p><xss>độc</xss><iframe src=\"https://evil.example\"></iframe>");
        assertNotEquals("<p>nội dung</p><xss>độc</xss><iframe src=\"https://evil.example\"></iframe>", result.html());
        assertTrue(result.html().contains("nội dung"), result.html());
        // <xss> (unknown tag) and <iframe> both removed = 2 elements reported.
        assertEquals(2, result.removedElements());
    }

    private static Set<String> stringSet(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return new HashSet<>(values);
    }
}
