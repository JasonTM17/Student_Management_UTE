const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const pagePath = 'src/app/admin/announcements/page.tsx';
const sortablePath = 'src/components/ui/sortable-list.tsx';
const copyPath = 'src/i18n/messages-sortable.ts';

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

/**
 * A module-scope `require` stand-in for a browser/Next bundle graph: any import
 * resolves to a callable proxy, so the pure helpers a module exports can be
 * exercised without React, `@/` aliases, or a DOM.
 */
function permissiveStub() {
  const factory = () => permissiveStub();
  return new Proxy(factory, {
    get(_target, prop) {
      if (prop === '__esModule') return true;
      return permissiveStub();
    },
    apply() {
      return permissiveStub();
    },
    construct() {
      return permissiveStub();
    },
  });
}

function loadExports(relativePath) {
  const output = ts.transpileModule(read(relativePath), {
    fileName: relativePath,
    reportDiagnostics: false,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React,
    },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(
    moduleRecord,
    moduleRecord.exports,
    () => permissiveStub(),
  );
  return moduleRecord.exports;
}

const pageSource = read(pagePath);
const sortableSource = read(sortablePath);
const pageModule = loadExports(pagePath);
const copyModule = loadExports(copyPath);

const { canReorder, summarizeDisplayOrderWrites } = pageModule;
const { reorderCopyEn, reorderCopyVi } = copyModule;

const PLAIN_FIRST_PAGE = { page: 1, filters: { semesterId: '', priority: '', status: 'ALL' } };

/** The `handleSaveReorder` body, sliced out of the page source for wiring asserts. */
function saveBody() {
  const start = pageSource.indexOf('const handleSaveReorder = async () => {');
  const end = pageSource.indexOf('const canAccess =');
  assert.ok(start > -1, 'handleSaveReorder must exist');
  assert.ok(end > start, 'handleSaveReorder must be declared before the guard it feeds');
  return pageSource.slice(start, end);
}

// --- (1) the reorder guard: page indexes may only be written as global ones ---

test('reorder is allowed on page 1 of the unfiltered feed', () => {
  const guard = canReorder(PLAIN_FIRST_PAGE);
  assert.equal(guard.allowed, true);
  assert.deepEqual(guard.blockedBy, []);
});

test('reorder is blocked while any status filter is active', () => {
  for (const status of ['ACTIVE', 'ARCHIVED', '', undefined]) {
    const guard = canReorder({ page: 1, filters: { ...PLAIN_FIRST_PAGE.filters, status } });
    assert.equal(guard.allowed, false, `status ${JSON.stringify(status)} must not allow a reorder`);
    assert.deepEqual(guard.blockedBy, ['filtered']);
  }
});

test('the shipped default filter leaves reorder locked until the admin clears it', () => {
  // The page opens on Status = Visible, so the very first thing an admin sees is
  // a locked reorder affordance rather than a silently global write.
  assert.match(pageSource, /status: 'ACTIVE',\s*\n\s*\}\);/);
  assert.equal(canReorder({ page: 1, filters: { semesterId: '', priority: '', status: 'ACTIVE' } }).allowed, false);
});

test('reorder is blocked by a semester or priority filter', () => {
  assert.deepEqual(
    canReorder({ page: 1, filters: { ...PLAIN_FIRST_PAGE.filters, semesterId: 'sem-1' } }).blockedBy,
    ['filtered'],
  );
  assert.deepEqual(
    canReorder({ page: 1, filters: { ...PLAIN_FIRST_PAGE.filters, priority: 'URGENT' } }).blockedBy,
    ['filtered'],
  );
});

test('an empty or whitespace-only filter value counts as cleared', () => {
  const guard = canReorder({ page: 1, filters: { semesterId: '   ', priority: '', status: 'ALL' } });
  assert.equal(guard.allowed, true);
});

test('reorder is blocked on every page but the first', () => {
  for (const page of [2, 3, 20]) {
    const guard = canReorder({ page, filters: PLAIN_FIRST_PAGE.filters });
    assert.equal(guard.allowed, false, `page ${page} must not allow a reorder`);
    assert.deepEqual(guard.blockedBy, ['paged']);
  }
});

test('a missing or impossible page number is treated as unsafe', () => {
  for (const page of [0, -1, Number.NaN, undefined]) {
    assert.equal(canReorder({ page, filters: PLAIN_FIRST_PAGE.filters }).allowed, false, `${page} must block`);
  }
});

test('a missing filter object is treated as filtered rather than as the plain feed', () => {
  const guard = canReorder({ page: 1, filters: undefined });
  assert.equal(guard.allowed, false);
  assert.deepEqual(guard.blockedBy, ['filtered']);
});

test('reorder lists every reason the admin has to clear, not just the first', () => {
  const guard = canReorder({ page: 4, filters: { semesterId: 'sem-1', priority: '', status: 'ACTIVE' } });
  assert.equal(guard.allowed, false);
  assert.deepEqual(guard.blockedBy, ['filtered', 'paged']);
});

test('the page consults the guard for both the affordance and the save', () => {
  assert.ok(pageSource.includes('canReorder('), 'the page must derive the guard from its own state');
  assert.ok(pageSource.includes('disabled={!canDragReorder}'), 'the drag affordance must switch off');
  assert.ok(pageSource.includes('<ReorderGuardNotice'), 'the reason must be explained inline');
  assert.ok(pageSource.includes('disabled={isSavingOrder || !canDragReorder}'), 'saving must be blocked too');
  const save = saveBody();
  assert.ok(
    save.indexOf('if (!guard.allowed)') < save.indexOf('Promise.allSettled'),
    'the save path must re-check the guard before it writes anything',
  );
});

// --- (2) a partial write must never look like a success ---

test('settled write accounting separates a full save from a partial one', async () => {
  const ok = (value) => Promise.resolve(value);
  const boom = () => Promise.reject(new Error('offline'));

  const allGood = summarizeDisplayOrderWrites(
    await Promise.allSettled([ok('a'), ok('b'), ok('c')]),
  );
  assert.deepEqual(allGood, { attempted: 3, failed: 0, allSucceeded: true });

  const twoBad = summarizeDisplayOrderWrites(
    await Promise.allSettled([ok('a'), boom(), boom()]),
  );
  assert.deepEqual(twoBad, { attempted: 3, failed: 2, allSucceeded: false });

  const allBad = summarizeDisplayOrderWrites(await Promise.allSettled([boom(), boom()]));
  assert.deepEqual(allBad, { attempted: 2, failed: 2, allSucceeded: false });

  assert.deepEqual(summarizeDisplayOrderWrites([]), { attempted: 0, failed: 0, allSucceeded: true });
});

test('the failure path cannot reach the success toast', () => {
  const save = saveBody();

  assert.ok(save.includes('Promise.allSettled('), 'writes must be settled, not swallowed');
  assert.doesNotMatch(save, /Promise\.all\(/, 'Promise.all never rejects a per-promise failure');
  assert.doesNotMatch(save, /\.catch\(/, 'a per-request catch turns a failed write into a fake success');
  assert.doesNotMatch(save, /console\.warn/, 'a failed write must reach the admin, not only the console');

  const successAt = save.indexOf('toast.success(');
  const failureAt = save.indexOf('if (!outcome.allSucceeded)');
  assert.ok(successAt > -1 && failureAt > -1);
  assert.ok(failureAt < successAt, 'the partial-write branch must run before any success toast');

  const failureBranch = save.slice(failureAt, successAt);
  assert.ok(failureBranch.includes('toast.error('), 'a partial write must raise an error toast');
  assert.ok(failureBranch.includes('return;'), 'a partial write must stop before the success toast');
  assert.ok(
    failureBranch.includes('fetchAnnouncements()'),
    'a partial write must reload so the UI returns to server truth',
  );
  assert.ok(
    failureBranch.includes('reorderCopy.result.partialFailure('),
    'the error toast must name how many items failed',
  );
});

test('the failure copy names the failed count and the reload', () => {
  assert.match(reorderCopyEn.result.partialFailure(3, 20), /3 of 20/);
  assert.match(reorderCopyEn.result.partialFailure(1, 2), /1 of 2/);
  assert.match(reorderCopyVi.result.partialFailure(3, 20), /3 \/ 20/);
});

// --- (3) a parent re-render must not destroy an in-flight drag ---

test('the page hands SortableList memoised callbacks, not inline arrows', () => {
  const listStart = pageSource.indexOf('<SortableList<AnnouncementRecord>');
  assert.ok(listStart > -1);
  // Everything the drag effect is configured with, up to the row renderer.
  const listBlock = pageSource.slice(listStart, pageSource.indexOf('renderItem={', listStart));
  assert.ok(listBlock.includes('disabled={!canDragReorder}'), 'the guard must reach the component');

  for (const prop of ['keyExtractor', 'onOrderChange', 'announceMove']) {
    const match = listBlock.match(new RegExp(`${prop}=\\{([^}]+)\\}`));
    assert.ok(match, `SortableList must receive ${prop}`);
    assert.match(
      match[1].trim(),
      /^[A-Za-z_$][\w$]*$/,
      `${prop} must be a stable identifier rather than a per-render arrow`,
    );
    assert.ok(
      pageSource.includes(`const ${match[1].trim()} = useCallback(`),
      `${prop} must be wrapped in useCallback`,
    );
  }
});

test('the sortable init effect no longer depends on caller callback identities', () => {
  const createAt = sortableSource.indexOf('Sortable.create(');
  assert.ok(createAt > -1);
  const depsMatch = sortableSource.slice(createAt).match(/\}, \[([^\]]*)\]\);/);
  assert.ok(depsMatch, 'the init effect must declare its dependencies');
  const deps = depsMatch[1].split(',').map((entry) => entry.trim()).filter(Boolean);
  assert.deepEqual(deps, ['animation', 'disabled', 'handleClassName', 'move']);
  for (const identity of ['onOrderChange', 'keyExtractor', 'announceMove', 'items', 'renderItem']) {
    assert.ok(
      !deps.includes(identity),
      `${identity} in the init deps would rebuild Sortable during a drag`,
    );
  }
});

test('move reads the latest caller callbacks through refs and stays stable itself', () => {
  assert.match(sortableSource, /orderChangeRef\.current\(currentItems, newKeys\)/);
  assert.match(sortableSource, /keyExtractorRef\.current\(item\)/);
  assert.match(sortableSource, /announceMoveRef\.current/);
  // The routine shared by the drag and the keyboard must be stable by construction.
  assert.match(
    sortableSource,
    /const move = useCallback\(\(oldIndex: number, newIndex: number\) => \{[\s\S]*?\}, \[\]\);/,
  );
});

// --- (4) the reorder must be audible, tappable, and gesture-safe ---

test('a reorder is announced to assistive technology', () => {
  assert.match(sortableSource, /aria-live="polite"/);
  assert.match(sortableSource, /\{liveMessage\}/);
  assert.match(sortableSource, /setLiveMessage\(/);
  const copy = reorderCopyEn.live.moved('Final exam schedule', 3, 1, 20);
  assert.match(copy, /Final exam schedule/);
  assert.match(copy, /position 3 of 20 to position 1/);
  assert.match(reorderCopyVi.live.moved('Lịch thi', 3, 1, 20), /vị trí 3 trong 20 sang vị trí 1/);
});

test('the drag handle clears the 44px minimum target and blocks scroll gestures', () => {
  const handleAt = sortableSource.indexOf('export function DragHandle');
  const handle = sortableSource.slice(handleAt);
  const baseClass = handle.match(/'drag-handle[^']*'/);
  assert.ok(baseClass, 'the handle keeps the drag-handle class SortableJS matches');

  const SIZE_PX = { 6: 24, 7: 28, 8: 32, 9: 36, 10: 40, 11: 44, 12: 48 };
  const box = baseClass[0].match(/\bh-(\d+) w-(\d+)\b/);
  assert.ok(box, 'the hit area is expressed as a Tailwind size pair');
  assert.ok(SIZE_PX[box[1]] >= 44, `height ${box[1]} is below the 44px target minimum`);
  assert.ok(SIZE_PX[box[2]] >= 44, `width ${box[2]} is below the 44px target minimum`);
  assert.match(baseClass[0], /\[touch-action:none\]/, 'a touch drag must not scroll the page instead');
  // The visible grip stays small inside the enlarged hit area.
  assert.match(handle, /flex h-7 w-7 items-center justify-center/);
});

test('the handle keeps a keyboard-operable, localized name', () => {
  assert.match(sortableSource, /role="button"/);
  assert.match(sortableSource, /tabIndex=\{0\}/);
  assert.match(sortableSource, /phím mũi tên/);
  assert.ok(pageSource.includes('label={reorderCopy.handle.label}'));
});

// --- localized copy wiring, kept out of the shared dictionary ---

test('both locales define the same reorder copy shape', () => {
  const leaves = (value, trail = []) =>
    Object.entries(value).flatMap(([key, child]) =>
      typeof child === 'function'
        ? [[...trail, key].join('.')]
        : typeof child === 'object' && child !== null
          ? leaves(child, [...trail, key])
          : [[...trail, key].join('.')],
    );
  assert.deepEqual(leaves(reorderCopyVi), leaves(reorderCopyEn));
  assert.ok(leaves(reorderCopyEn).length >= 12, 'the guard, dialog, handle, live, and result strings are all present');

  for (const key of leaves(reorderCopyEn)) {
    const get = (dict) => key.split('.').reduce((acc, part) => acc[part], dict);
    const enValue = get(reorderCopyEn);
    if (typeof enValue !== 'string') continue;
    assert.notEqual(
      get(reorderCopyVi),
      enValue,
      `${key} must carry a real Vietnamese translation, not the English string`,
    );
  }
});

test('the new copy lives in its own module and not in the shared dictionary', () => {
  assert.ok(!read('src/i18n/messages.ts').includes('reorderCopy'), 'messages.ts stays untouched by this fix');
  assert.ok(pageSource.includes("from '@/i18n/messages-sortable'"));
  assert.ok(pageSource.includes('reorderCopy.guard') || pageSource.includes('copy.guard'));
});

test('the feed still explains the guard in the dialog that owns the handles', () => {
  const dialogStart = pageSource.indexOf("{modal === 'reorder'");
  assert.ok(dialogStart > -1);
  const dialog = pageSource.slice(dialogStart);
  const dialogBlock = dialog.slice(0, dialog.indexOf('{confirmationDialog}'));
  assert.ok(dialogBlock.includes('<ReorderGuardNotice'), 'the dialog states what to clear');
  assert.ok(dialogBlock.includes('reorderCopy.dialog.readOnly'), 'the list is labelled read-only');
  assert.ok(dialogBlock.includes('reorderCopy.dialog.intro'), 'the drag instruction is localized');
});

test('the editor swaps only ranks it read, so a rank-less notice is never pinned', () => {
  const editor = read('src/app/dashboard/editor/page.tsx');
  const saveStart = editor.indexOf('const handleSaveNoticeOrder');
  assert.ok(saveStart > -1);
  const save = editor.slice(saveStart, editor.indexOf('const fetchPublishedNotices'));

  // "displayOrder" is a nullable 32-bit INTEGER (V70), so a substituted
  // sentinel would either pin a notice the admin never pinned or overflow.
  assert.ok(!save.includes('MAX_SAFE_INTEGER'), 'no synthetic rank stands in for a missing one');
  assert.ok(save.includes("typeof n.displayOrder === 'number'"), 'only ranked notices join the swap');
  const rankWrites = save.match(/updateDisplayOrder\(/g) ?? [];
  assert.equal(rankWrites.length, 1, 'one write site, fed by the ranks already in use');
  assert.ok(save.includes('slots[index]'), 'the dragged rows reuse the occupied ranks');
  assert.ok(
    save.includes("outcome.status === 'rejected'") && save.includes('return;'),
    'a partial write reloads from the server instead of reporting success',
  );
});
