-- One-way hygiene pass for notification deep links written before the
-- same-origin allowlist landed: rows whose stored link is not a single-slash
-- relative path get link = NULL so clients never emit them as anchors. The
-- allowlist mirrors NotificationLinkPolicy: '/', no second '/', no backslash,
-- no control or whitespace characters. Safe values are untouched, and an
-- already-clean database is a no-op.
UPDATE notifications.notification
SET link = NULL
WHERE link IS NOT NULL
  AND link !~ '^/([^/\\[:space:][:cntrl:]][^\\[:space:][:cntrl:]]*)?$';
