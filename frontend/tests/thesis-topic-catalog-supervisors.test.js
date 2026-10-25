const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function readSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

const CATALOG = 'src/components/dashboard/thesis/ThesisTopicCatalogPage.tsx';
const DETAIL = 'src/components/dashboard/thesis/ThesisTopicDetailPage.tsx';
const API = 'src/lib/thesis-api.ts';

// The catalog must build its "choose advisor first" step from the topic list
// payload itself. A per-topic supervisor request per card is the N+1 defect
// this contract guards against.

test('thesis catalog derives advisors from the topic payload, no per-topic fan-out', () => {
  const catalog = readSource(CATALOG);
  assert.ok(
    !catalog.includes('listSupervisors'),
    'the catalog must not call the per-topic supervisor endpoint',
  );
  assert.ok(
    !catalog.includes('topicSupervisorsMap'),
    'the fan-out result map must stay removed',
  );
  assert.ok(
    !/useEffect\s*\(/.test(catalog),
    'the catalog must not reload advisors in an effect',
  );
  assert.ok(catalog.includes('topic.supervisors'), 'advisor data comes from the list payload');
  assert.ok(catalog.includes('uniqueSupervisors'), 'the choose-advisor-first select stays');
});

test('thesis catalog keeps the advisor select and topic-count label', () => {
  const catalog = readSource(CATALOG);
  assert.ok(catalog.includes('selectedSupervisorId'), 'advisor selection state remains');
  assert.ok(catalog.includes('topicCount'), 'each advisor option still shows its topic count');
  assert.ok(
    catalog.includes('(topic.supervisors ?? []).some((s) => s.lecturerId === selectedSupervisorId)'),
    'advisor filtering reads the payload field',
  );
});

test('ThesisTopic types the additive supervisors field', () => {
  const api = readSource(API);
  const topicStart = api.indexOf('export interface ThesisTopic ');
  assert.ok(topicStart > -1, 'ThesisTopic interface must exist');
  const topicBlock = api.slice(topicStart, topicStart + 1800);
  assert.ok(
    /supervisors\?:\s*ThesisTopicSupervisor\[\]/.test(topicBlock),
    'ThesisTopic must type the optional additive supervisors array',
  );
  assert.ok(
    /interface ThesisTopicSupervisor[\s\S]*lecturerId[\s\S]*firstName[\s\S]*lastName/.test(api),
    'ThesisTopicSupervisor keeps the name fields the list projection carries',
  );
});

test('the topic detail page keeps its single-topic supervisor call', () => {
  const detail = readSource(DETAIL);
  assert.ok(detail.includes('listSupervisors'), 'the detail page keeps the per-topic endpoint');
});
