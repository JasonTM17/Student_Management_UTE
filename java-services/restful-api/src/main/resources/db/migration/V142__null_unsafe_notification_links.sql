-- One-way hygiene pass for notification deep links written before the
-- same-origin allowlist landed: rows whose stored link is not a safe
-- single-slash relative path get link = NULL so clients never emit them as
-- anchors. The allowlist mirrors NotificationLinkPolicy / safe-path.ts / the
-- shared corpus (src/test/resources/notification/link-corpus.json): a leading
-- '/', no second '/', no backslash, no '.' or '..' path segment, no
-- percent-encoded separator or dot (%2e/%2f/%5c/%25), and no Unicode
-- separator/format/control code point. The character test enumerates explicit
-- code-point ranges instead of locale-sensitive classes, so the cleanup is
-- identical under any server locale. Safe values are untouched, and an
-- already-clean database is a no-op.
-- The cleanup is intentionally narrower than the runtime allowlists on
-- private-use/unassigned points (a new-write rule, not a cleanup target) and
-- intentionally wider on the U+E0100 variation-selector supplement.
--
-- Edited in place pre-deploy; local scratch databases that already ran the
-- earlier revision need a flyway_schema_history checksum refresh or a
-- recreate, because this file's checksum changed.
UPDATE notifications.notification
SET link = NULL
WHERE link IS NOT NULL
  AND (
       link !~ '^/([^/\\][^\\]*)?$'
       OR link ~ '(^|/)\.\.?(/|$)'
       OR link ~* '%(2e|2f|5c|25)'
       OR EXISTS (
            SELECT 1
            FROM regexp_split_to_table(link, '') AS ch
            WHERE ascii(ch) BETWEEN 0 AND 32        -- C0 controls + space
               OR ascii(ch) BETWEEN 127 AND 160     -- DEL, C1 controls, NBSP
               OR ascii(ch) = 173                   -- soft hyphen (Cf)
               OR ascii(ch) BETWEEN 1536 AND 1541   -- U+0600..U+0605 (Cf)
               OR ascii(ch) = 1564                  -- U+061C (Cf)
               OR ascii(ch) = 1757                  -- U+06DD (Cf)
               OR ascii(ch) = 1807                  -- U+070F (Cf)
               OR ascii(ch) BETWEEN 2192 AND 2193   -- U+0890..U+0891 (Cf)
               OR ascii(ch) = 2274                  -- U+08E2 (Cf)
               OR ascii(ch) = 5760                  -- U+1680 (Zs)
               OR ascii(ch) = 6158                  -- U+180E (Cf)
               OR ascii(ch) BETWEEN 8192 AND 8207   -- U+2000..U+200F (Zs + Cf)
               OR ascii(ch) BETWEEN 8232 AND 8238   -- U+2028..U+202E (Zl, Zp, Cf)
               OR ascii(ch) = 8239                  -- U+202F (Zs)
               OR ascii(ch) BETWEEN 8287 AND 8303   -- U+205F..U+206F (Zs + Cf)
               OR ascii(ch) = 12288                 -- U+3000 (Zs)
               OR ascii(ch) = 65279                 -- U+FEFF (Cf)
               OR ascii(ch) BETWEEN 65529 AND 65531 -- U+FFF9..U+FFFB (Cf)
               OR ascii(ch) BETWEEN 119155 AND 119162 -- U+1D173..U+1D17A (Cf)
               OR ascii(ch) = 917505                -- U+E0001 (Cf)
               OR ascii(ch) BETWEEN 917536 AND 917631 -- U+E0020..U+E007F (Cf)
               OR ascii(ch) BETWEEN 917760 AND 917999 -- U+E0100..U+E01EF (Cf)
        )
  );
