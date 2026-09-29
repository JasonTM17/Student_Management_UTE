package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Pins the RAG gateway timeout contract in application.yml: the read timeout
 * default is 15000ms (a broken remote chain degrades to the local lexical
 * fallback after 15s instead of holding the user for 30s) while the Supabase
 * knowledge-sync timeout stays untouched at its own 30000ms default — the two
 * property families must never be coupled.
 */
class AssistantRagPropertiesTest {

    private static Binder binderFromApplicationYaml() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        // The placeholders resolver turns "${ENV:default}" into the default so
        // the bind sees the shipped defaults, not the literal placeholder —
        // and never the operator's environment (this machine's env is not
        // consulted, keeping the assertion deterministic).
        return new Binder(ConfigurationPropertySources.from(propertySources),
                new PropertySourcesPlaceholdersResolver(propertySources));
    }

    @Test
    void applicationYamlDefaultsTheRagReadTimeoutTo15000() throws Exception {
        AssistantRagProperties properties = binderFromApplicationYaml()
                .bind("assistant.rag", Bindable.of(AssistantRagProperties.class)).get();

        // Bug do-tre-1: a broken remote chain must degrade after 15s, not 30s.
        assertEquals(15000, properties.readTimeoutMs());
        assertEquals(1000, properties.connectTimeoutMs());
    }

    @Test
    void constructorAcceptsTheNewReadTimeoutUnclamped() {
        AssistantRagProperties properties =
                new AssistantRagProperties("", "", false, 1000, 15000);
        assertEquals(15000, properties.readTimeoutMs());
        assertEquals(1000, properties.connectTimeoutMs());
    }

    @Test
    void tinyReadTimeoutsStillClampToTheOneSecondFloor() {
        AssistantRagProperties properties =
                new AssistantRagProperties("", "", false, 500, 500);
        assertEquals(1000, properties.readTimeoutMs());
        // The connect floor is 250ms, so 500 stays 500 — only the read timeout
        // has the 1s floor this regression pins.
        assertEquals(500, properties.connectTimeoutMs());
    }

    @Test
    void supabaseKnowledgeSyncKeepsItsOwnThirtySecondDefault() throws Exception {
        SupabaseKnowledgeProperties properties = binderFromApplicationYaml()
                .bind("assistant.supabase", Bindable.of(SupabaseKnowledgeProperties.class)).get();

        // Pin the separation: lowering the RAG timeout must not move the
        // Supabase sync/authority read timeout.
        assertEquals(30000, properties.readTimeoutMs());
    }
}
