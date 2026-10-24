package io.campuscore.restfulapi.thesis.assistant.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Explicit injection concepts must be grounded, not inferred from a generic Spring hit. */
final class SpringInjectionQuestionFocus {
    private record Concept(String canonical, List<String> aliases) {}

    private static final List<Concept> CONCEPTS = List.of(
            new Concept("dependency injection", List.of("dependency injection", "tiem phu thuoc")),
            new Concept("constructor injection", List.of("constructor injection", "tiem qua constructor", "tiem qua ham khoi tao")),
            new Concept("setter injection", List.of("setter injection", "tiem qua setter")),
            new Concept("field injection", List.of("field injection", "tiem qua truong")));
    private static final Pattern EXAMPLE = Pattern.compile("\\b(example|snippet|code|vi du|mau ma)\\b");
    private static final Pattern CODE_BLOCK = Pattern.compile("(?s)```(?:java)?\\s*\\R(.+?)\\R```");
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*)");
    private static final Pattern NON_CODE = Pattern.compile(
            "(?s)\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\r\\n]*|/\\*.*?\\*/");

    private SpringInjectionQuestionFocus() {}

    static List<String> retrievalPhrases(String question) {
        String folded = fold(question);
        List<String> phrases = new ArrayList<>();
        for (Concept concept : CONCEPTS) {
            if (mentions(folded, concept)) phrases.add(concept.canonical());
        }
        return phrases;
    }

    static boolean coveredBy(String question, String content) {
        String foldedQuestion = fold(question);
        String foldedContent = fold(content);
        boolean focused = false;
        for (Concept concept : CONCEPTS) {
            if (mentions(foldedQuestion, concept)) {
                focused = true;
                if (!mentions(foldedContent, concept)) return false;
            }
        }
        // These articles are served verbatim. An example request therefore
        // needs actual code in the article, not only an example keyword.
        if (!focused || !EXAMPLE.matcher(foldedQuestion).find()) return true;
        boolean constructorRequested = mentions(foldedQuestion, CONCEPTS.get(1));
        var blocks = CODE_BLOCK.matcher(content == null ? "" : content);
        while (blocks.find()) {
            if (!constructorRequested) return true;
            String code = NON_CODE.matcher(blocks.group(1)).replaceAll(" ");
            var classes = CLASS_NAME.matcher(code);
            while (classes.find()) {
                // A declaration with at least one parameter grounds injection;
                // an unrelated fenced snippet or a no-arg constructor does not.
                if (Pattern.compile("(?:^|[;{}])\\s*(?:@[\\p{L}\\p{N}_$.]+(?:\\s*\\([^)]*\\))?\\s*)*"
                        + "(?:(?:public|protected|private)\\s+)?" + Pattern.quote(classes.group(1))
                        + "\\s*\\(\\s*[\\p{L}_$][^)]*\\)\\s*\\{").matcher(code).find()) return true;
            }
        }
        return false;
    }

    private static boolean mentions(String folded, Concept concept) {
        return concept.aliases().stream().anyMatch(alias -> folded.contains(" " + alias + " "));
    }

    private static String fold(String value) {
        return " " + Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
    }
}
