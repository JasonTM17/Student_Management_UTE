// Wait for the afb7f802 deploy (search param accepted on /announcements)
// then verify the V131 data cleanup and the new admin search filters.
const BASE = 'https://campuscore-backend-p4em.onrender.com/api/v1';
const CREDS = { email: 'admin@campuscore.edu', password: 'password123' };

async function login() {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(CREDS),
    redirect: 'manual',
  });
  const cookie = (res.headers.get('set-cookie') || '').split(';')[0];
  if (!res.ok && res.status !== 204) throw new Error(`login ${res.status}`);
  return cookie;
}

async function get(cookie, path) {
  const res = await fetch(`${BASE}${path}`, { headers: { Cookie: cookie } });
  let body = null;
  try { body = await res.json(); } catch { /* empty */ }
  return { status: res.status, body };
}

const cookie = await login();
const deadline = Date.now() + 20 * 60 * 1000;
let live = false;
while (Date.now() < deadline) {
  const { status } = await get(cookie, '/announcements?search=x&limit=1');
  if (status === 200) { live = true; break; }
  console.log(`waiting... /announcements?search -> ${status}`);
  await new Promise(r => setTimeout(r, 30000));
}
if (!live) { console.log('DEPLOY TIMEOUT'); process.exit(1); }
console.log('NEW BUILD LIVE (search param accepted)');

const checks = [];
function report(name, ok, detail) { checks.push([name, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${detail}`); }

// 1. announcements search
let r = await get(cookie, '/announcements?search=Java&limit=10');
report('ann search Java', r.status === 200 && (r.body?.meta?.total ?? 0) > 0, `total=${r.body?.meta?.total}`);
r = await get(cookie, '/announcements?search=zzz-no-match-zzz&limit=10');
report('ann search no-match', r.status === 200 && r.body?.meta?.total === 0, `total=${r.body?.meta?.total}`);

// 2. audit search
r = await get(cookie, '/admin/audit?search=announcement&limit=10');
report('audit search', r.status === 200 && (r.body?.meta?.total ?? 0) > 0, `total=${r.body?.meta?.total}`);
r = await get(cookie, '/admin/audit?search=%25_PROBE%25&limit=10');
report('audit search literal %', r.status === 200, `total=${r.body?.meta?.total}`);

// 3. E2E probe announcements gone + snapshots filled
r = await get(cookie, '/announcements?limit=100&status=ARCHIVED');
const anns = r.body?.data || [];
const probes = anns.filter(a => /E2E|PROBE|probe/i.test(a.title || ''));
report('no probe announcements', probes.length === 0, `found=${probes.length}`);
r = await get(cookie, '/announcements?limit=100');
const nullSnap = (r.body?.data || []).filter(a => a.section && a.section.id && !a.section.sectionNumber);
report('snapshots filled', nullSnap.length === 0, `null=${nullSnap.length}`);

// 4. lecturer phone/office
r = await get(cookie, '/lecturers?limit=100');
const lecs = r.body?.data || [];
const noContact = lecs.filter(l => !l.phone || !l.office);
report('lecturer contact filled', noContact.length === 0, `null=${noContact.length}/${lecs.length}`);

// 5. faculty dean
r = await get(cookie, '/faculties?limit=50');
const facs = r.body?.data || [];
const noDean = facs.filter(f => !f.dean);
report('faculty dean filled', noDean.length === 0, `null=${noDean.length}/${facs.length}`);

// 6. section classroomId
r = await get(cookie, '/sections?limit=100');
const secs = r.body?.data || [];
const noRoom = secs.filter(s => !s.classroomId && !s.classroom?.id);
report('section classroom filled', noRoom.length === 0, `null=${noRoom.length}/${secs.length}`);

// 7. student notifications free of E2E probe
const res2 = await fetch(`${BASE}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'student@campuscore.edu', password: 'password123' }), redirect: 'manual',
});
const scookie = (res2.headers.get('set-cookie') || '').split(';')[0];
r = await get(scookie, '/notifications?limit=100');
const stuNotifs = r.body?.data || [];
const stuProbe = stuNotifs.filter(n => /E2E|PROBE/i.test(n.title || '') || /E2E|PROBE/i.test(n.message || ''));
report('student notif clean', stuProbe.length === 0, `probe=${stuProbe.length}/${stuNotifs.length}`);

const fails = checks.filter(c => !c[1]).length;
console.log(`\n${checks.length - fails}/${checks.length} PASS`);
process.exit(fails ? 1 : 0);
