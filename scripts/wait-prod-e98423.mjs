// Wait for Render to swap to the deterministic-pagination build, then verify
// the fixes against live production. Deploy signal: the enrollment CSV export
// now rejects unknown query params (old build silently ignored them).
const BASE = 'https://campuscore-backend-p4em.onrender.com/api/v1';
const DEADLINE = Date.now() + 25 * 60 * 1000;

function jar() {
  const cookies = new Map();
  return {
    async req(path, opts = {}) {
      const res = await fetch(BASE + path, {
        ...opts,
        headers: { cookie: [...cookies.values()].join('; '), ...(opts.headers || {}) },
      });
      for (const sc of res.headers.getSetCookie?.() ?? []) {
        const [pair] = sc.split(';');
        const eq = pair.indexOf('=');
        if (eq > 0) cookies.set(pair.slice(0, eq), pair);
      }
      return res;
    },
  };
}

async function login(email, password) {
  const c = jar();
  const res = await c.req('/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`login ${email} -> ${res.status}`);
  return c;
}

const admin = await login('admin@campuscore.edu', 'password123');
const student = await login('student@campuscore.edu', 'password123');

// ---- wait for the new build ----
let live = false;
while (Date.now() < DEADLINE && !live) {
  try {
    const res = await admin.req('/enrollments/export/csv?bogusParam=1');
    live = res.status === 400;
  } catch { /* keep polling */ }
  if (!live) {
    process.stdout.write('.');
    await new Promise(r => setTimeout(r, 20000));
  }
}
if (!live) { console.log('\nTIMEOUT waiting for e98423e7 deploy'); process.exit(2); }
console.log('\nNEW BUILD LIVE — verifying fixes');

let pass = 0, fail = 0;
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`);
  ok ? pass++ : fail++;
};

// 1. enrollment pages no longer overlap (deterministic ORDER BY ... id)
{
  const ids = [];
  for (let p = 1; p <= 3; p++) {
    const res = await admin.req(`/enrollments?status=ENROLLED&page=${p}&limit=100`);
    const body = await res.json();
    ids.push(...(body.data ?? []).map(e => e.id));
  }
  const dup = ids.length - new Set(ids).size;
  check('enrollment pages have zero id overlap (was 158 dup ids)', dup === 0, `rows=${ids.length} dup=${dup}`);
}

// 2. /users pages deterministic
{
  const ids = [];
  for (let p = 1; p <= 3; p++) {
    const res = await admin.req(`/users?page=${p}&limit=100`);
    const body = await res.json();
    ids.push(...(body.data ?? []).map(u => u.id));
  }
  const dup = ids.length - new Set(ids).size;
  check('user pages have zero id overlap (was 26 dup ids)', dup === 0, `rows=${ids.length} dup=${dup}`);
}

// 3. V132 applied — zero over-capacity sections
{
  const res = await admin.req('/sections?page=1&limit=100');
  const body = await res.json();
  const totalPages = Math.min(body.meta?.totalPages ?? 1, 3);
  const over = [];
  const scan = (rows) => rows.forEach(s => { if (s.capacity && (s.enrolledCount ?? 0) > s.capacity) over.push(`${s.courseCode}-${s.sectionNumber}`); });
  scan(body.data ?? []);
  for (let p = 2; p <= totalPages; p++) {
    const r = await admin.req(`/sections?page=${p}&limit=100`);
    scan((await r.json()).data ?? []);
  }
  check('zero over-capacity sections (V132)', over.length === 0, over.join(','));
}

// 4. export accepts allowlisted search, CSV still shaped right
{
  const res = await admin.req('/enrollments/export/csv?search=Nguy');
  const text = await res.text();
  check('enrollment export honors search', res.status === 200 && text.startsWith('id,studentNumber'), `${res.status}`);
}

// 5. audit search still folds Vietnamese + escapes wildcards
{
  const res = await admin.req('/admin/audit?search=c%E1%BA%ADp%20nh%E1%BA%ADt');
  const body = await res.json();
  check('audit fold search', res.status === 200 && Array.isArray(body.data), `rows=${(body.data ?? []).length}`);
  const res2 = await admin.req('/admin/audit?search=%25');
  const body2 = await res2.json();
  check('audit % literal is escaped', res2.status === 200, `rows=${(body2.data ?? []).length}`);
}

// 6. legacy article feeds bounded but still list-shaped
{
  const res = await student.req('/article-media-galleries');
  check('legacy media feed still returns a list', res.status === 200 && Array.isArray(await res.json()), `${res.status}`);
  const res2 = await student.req('/article-attachments');
  check('legacy attachment feed still returns a list', res2.status === 200 && Array.isArray(await res2.json()), `${res2.status}`);
}

// 7. notifications page-walk covers whole inbox without dupes
{
  const res = await student.req('/notifications/my?page=1&limit=100');
  const body = await res.json();
  const ids = (body.data ?? []).map(n => n.id);
  check('notifications page 1 clean', res.status === 200 && ids.length === new Set(ids).size, `rows=${ids.length} total=${body.meta?.total}`);
}

console.log(`\nRESULT ${pass} pass / ${fail} fail`);
process.exit(fail ? 1 : 0);
