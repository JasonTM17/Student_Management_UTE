import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('selected thesis topic survives topic, detail, and registration navigation', () => {
  const types = read('src/navigation/types.ts');
  const navigator = read('src/navigation/MobileNavigator.tsx');
  const thesis = read('src/screens/thesis/ThesisScreens.tsx');

  assert.match(types, /thesisTopicId\?: string/);
  assert.match(types, /selectedThesisTopicId: string \| null/);
  assert.match(navigator, /setSelectedThesisTopicId\(options\.thesisTopicId\)/);
  assert.match(navigator, /selectedThesisTopicId=\{selectedThesisTopicId\}/);
  assert.match(thesis, /navigate\('thesis\.detail', \{ thesisTopicId: topic\.id \}\)/);
  assert.match(thesis, /navigate\('thesis\.registration', \{ thesisTopicId: topic\.id \}\)/);
  assert.match(thesis, /item\.id === \(selectedThesisTopicId \?\? group\?\.topicId\)/);
  assert.doesNotMatch(thesis, /const topic = workspace\.topics\[0\]/);
});

test('shared mobile primitives announce headings, progress, and unread state', () => {
  const ui = read('src/components/Ui.tsx');
  const notifications = read('src/screens/student/StudentScreens.tsx');

  assert.match(ui, /accessibilityRole="header" variant="headlineLarge"/);
  assert.match(ui, /accessibilityRole="header" variant="headlineSmall"/);
  assert.match(ui, /accessibilityRole="progressbar"/);
  assert.match(ui, /accessibilityValue=\{\{ min: 0, max: 100, now: Math\.round\(boundedValue\) \}\}/);
  assert.match(ui, /unread \? 'Unread' : null/);
  assert.match(ui, />\s*Unread\s*</);
  assert.match(notifications, /accessibilityLiveRegion="polite"/);
});

test('student navigation and registration stay usable at a narrow mobile width', () => {
  const routes = read('src/navigation/routes.ts');
  const bottomNavigation = read('src/components/BottomNavigation.tsx');
  const studentScreens = read('src/screens/student/StudentScreens.tsx');

  assert.match(routes, /student:\s*\[[\s\S]*?route: 'registration', label: 'Register'/);
  assert.match(bottomNavigation, /accessibilityRole="tablist"/);
  assert.match(bottomNavigation, /accessibilityRole="tab"/);
  assert.match(bottomNavigation, /minWidth: 0/);
  assert.match(bottomNavigation, /adjustsFontSizeToFit/);
  assert.match(studentScreens, /registrationHeroHeader/);
  assert.match(studentScreens, /registrationMeta/);
  assert.match(studentScreens, /label=\{section\.seats\}/);
  assert.doesNotMatch(studentScreens, /ROUND 02|4 days left|next round closes in 4 days/i);
});

test('staff copy stays inside the retained academic course scope', () => {
  const copy = [
    'src/navigation/routes.ts',
    'src/components/MenuPanel.tsx',
    'src/screens/operations/OperationsScreens.tsx',
    'README.md',
  ].map(read).join('\n');

  assert.doesNotMatch(
    copy,
    /\b(?:finance|financial|billing|invoice|payment|reporting|operational|operations)\b/i,
  );
  assert.match(copy, /family: 'staff'/);
});

test('npm test runs the atlas and Phase 6 regressions explicitly', () => {
  const packageJson = JSON.parse(read('package.json'));

  assert.match(packageJson.scripts.test, /screen-atlas\.test\.mjs/);
  assert.match(packageJson.scripts.test, /phase-six-repair\.test\.mjs/);
});

test('mobile assistant keeps native transport buffered and owner-history operations explicit', () => {
  const client = read('src/api/client.ts');
  const assistant = read('src/screens/assistant/AssistantScreen.tsx');

  assert.match(client, /assistantConversations: '\/thesis\/assistant\/conversations'/);
  assert.match(client, /\/messages/);
  assert.match(client, /deleteAssistantConversation/);
  assert.match(client, /signal\?: AbortSignal|RequestInit/);
  assert.match(assistant, /conversationId/);
  assert.match(assistant, /deleteConversation/);
  assert.match(assistant, /lastFailedPrompt/);
  assert.match(assistant, /citation\.excerpt/);
  assert.doesNotMatch(assistant, /EventSource|text\/event-stream/);
  assert.doesNotMatch(client, /DEEPSEEK_API_KEY|sk-[A-Za-z0-9]/);
});

test('mobile assistant preserves idempotency, server cancellation, pagination, and fixed feedback reasons', () => {
  const client = read('src/api/client.ts');
  const assistant = read('src/screens/assistant/AssistantScreen.tsx');

  assert.match(client, /createAssistantClientRequestId/);
  assert.match(client, /clientRequestId:/);
  assert.match(client, /X-Next-Cursor/);
  assert.match(client, /assistantCancel: '\/thesis\/assistant\/requests'/);
  assert.match(client, /cancelAssistantRequest/);
  assert.match(client, /AssistantFeedbackReason/);
  assert.match(client, /putAssistantFeedback/);
  assert.match(assistant, /lastFailedClientRequestId/);
  assert.match(assistant, /cancelAssistantRequest\(clientRequestId\)/);
  assert.match(assistant, /finally \{ abortRef\.current\?\.abort\(\)/);
  assert.match(assistant, /pendingDeleteId/);
  assert.match(assistant, /sessionExpired/);
  assert.match(assistant, /offline/);
});

test('mobile never replays a terminal or fenced request key', () => {
  const assistant = read('src/screens/assistant/AssistantScreen.tsx');

  assert.match(assistant, /const NON_REPLAYABLE_TERMINAL_CODES = new Set\(\[[\s\S]*'FAILED_AMBIGUOUS'[\s\S]*'TURN_TERMINAL_RACE'[\s\S]*'TURN_NOT_ACTIVE'/);
  assert.match(assistant, /!NON_REPLAYABLE_TERMINAL_CODES\.has\(requestError\?\.code \?\? ''\)/);
  assert.match(assistant, /'TURN_CANCELLED'/);
  assert.match(assistant, /'TURN_PURGED'/);
});
