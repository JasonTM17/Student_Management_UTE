#!/usr/bin/env node
'use strict';

try {
  const fs = require('fs');
  const {
    createSessionStateContext,
    isHookEnabled
  } = require('./lib/ck-config-utils.cjs');
  const {
    persistProjectCheckpoint,
    refreshStatuslineSnapshot
  } = require('./lib/session-state-manager.cjs');

  if (!isHookEnabled('session-state')) process.exit(0);

  const TRACKED_POST_TOOL_EVENTS = new Set(['Agent', 'Task', 'TaskCreate', 'TaskUpdate', 'TodoWrite']);

  async function main() {
    const stdin = fs.readFileSync(0, 'utf8').trim();
    const data = stdin ? JSON.parse(stdin) : {};
    const eventType = data.hook_event_name || null;
    const context = createSessionStateContext({
      sessionId: data.session_id,
      cwd: process['env'].CK_PROJECT_ROOT || data.cwd || process.cwd(),
      requireBinding: true
    });
    if (!context) process.exit(0);

    if (eventType === 'PostToolUse' && TRACKED_POST_TOOL_EVENTS.has(data.tool_name || '')) {
      await refreshStatuslineSnapshot(context, data);
    } else if (eventType === 'SubagentStop') {
      await refreshStatuslineSnapshot(context, data);
    } else if (eventType === 'Stop') {
      await persistProjectCheckpoint(context, data);
    }

    if (eventType === 'PostToolUse') {
      process.stdout.write(JSON.stringify({ continue: true }));
    }
    process.exit(0);
  }

  main().catch(() => process.exit(0));
} catch {
  process.exit(0);
}
