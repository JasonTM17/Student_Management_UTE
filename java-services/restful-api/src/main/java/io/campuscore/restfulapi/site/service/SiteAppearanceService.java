package io.campuscore.restfulapi.site.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.campuscore.restfulapi.site.dto.SiteAppearancePayload;
import io.campuscore.restfulapi.site.repository.SiteAppearanceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Site appearance read/write orchestration. The service enforces that the
 * payload exists, parses to a JSON object and stays within a hard size bound
 * so an administrator cannot wedge the table, then stamps every write
 * ({@code version}, {@code updatedAt}) so a polling browser can tell one save
 * from the next. SQL and the row shape live in {@link SiteAppearanceRepository}.
 */
@Service
public class SiteAppearanceService {

    /** Matches the payload CHECK constraint in V73. */
    public static final int MAX_PAYLOAD_CHARS = 131_072;

    private static final String VERSION_FIELD = "version";
    private static final String UPDATED_AT_FIELD = "updatedAt";

    private final SiteAppearanceRepository repository;
    private final ObjectMapper objectMapper;

    public SiteAppearanceService(SiteAppearanceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * @return the stored payload, or {@code null} when no appearance has been
     *         saved yet (the caller answers with an empty object and the
     *         frontend sanitizer fills the defaults).
     */
    public SiteAppearancePayload read() {
        String stored = repository.read();
        return stored == null ? null : new SiteAppearancePayload(stored);
    }

    /**
     * Validates and upserts the payload. The stored payload carries the write
     * stamp: {@code version} and {@code updatedAt} are overwritten here, so a
     * browser polling this row can tell one save from the next. Echoing the
     * caller's payload instead would make a save from one machine invisible to
     * every other machine.
     *
     * @param payload raw JSON object text; must already parse to a JSON object
     *                and fit within {@link #MAX_PAYLOAD_CHARS}
     * @param updatedBy immutable actor id (JWT subject) of the writer
     * @return the payload text as it was stored, stamp included
     */
    public SiteAppearancePayload write(SiteAppearancePayload payload, String updatedBy) {
        String json = payload == null ? null : payload.json();
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        if (json.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("payload exceeds the size limit");
        }

        ObjectNode stamped;
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (!parsed.isObject()) {
                throw new IllegalArgumentException("payload must be a JSON object");
            }
            stamped = (ObjectNode) parsed;
        } catch (JacksonException malformed) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }

        long stamp = nextStamp();
        stamped.put(VERSION_FIELD, stamp);
        stamped.put(UPDATED_AT_FIELD, Instant.ofEpochMilli(stamp).toString());
        String storedPayload = stamped.toString();
        if (storedPayload.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("payload exceeds the size limit");
        }

        repository.upsert(storedPayload, updatedBy);

        String persisted = repository.read();
        if (persisted == null) {
            throw new IllegalStateException("site appearance write did not persist");
        }
        return new SiteAppearancePayload(persisted);
    }

    /**
     * Monotonic in the same millisecond: two saves that land on one clock tick
     * must still look like two changes to the reader's change detection.
     */
    private long nextStamp() {
        long previous = previousStamp();
        long now = System.currentTimeMillis();
        return Math.max(now, previous + 1);
    }

    private long previousStamp() {
        String stored = repository.read();
        if (stored == null || stored.isBlank()) {
            return 0;
        }
        try {
            JsonNode version = objectMapper.readTree(stored).get(VERSION_FIELD);
            return version == null || !version.isNumber() ? 0 : Math.max(0, version.asLong());
        } catch (JacksonException malformed) {
            // A row this store never wrote has no version to carry forward; the
            // wall-clock stamp supersedes it and this write repairs the payload.
            return 0;
        }
    }
}
