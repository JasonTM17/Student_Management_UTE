// Production smoke: read-only end-to-end verification against a deployed
// environment. Run after every deploy:
//   node scripts/prod-smoke.mjs                      # prod (www.campusute.io.vn)
//   BASE_URL=http://localhost:3100 node scripts/prod-smoke.mjs   # local stack
// Covers: demo-account logins, thesis trio roster, curriculum plan,
// assistant grounding, admin user visibility. Mutating checks (enroll/drop)
// stay opt-in via SMOKE_MUTATE=1 so a routine run never changes prod data.
const BASE = process.env.BASE_URL || 'https://www.campusute.io.vn';
const API = `${BASE}/api/v1`;
const PASSWORD = process.env.DEMO_PASSWORD || 'password123';
const ROUND_ID = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60';

let failures = 0;
function check(label, condition, detail = '') {
  if (condition) {
    console.log(`PASS  ${label}`);
  } else {
    failures += 1;
    console.error(`FAIL  ${label} ${detail}`);
  }
}

async function login(email) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  const cookies = res.headers.getSetCookie?.() || [];
  const jar = {};
  for (const c of cookies) {
    const [pair] = c.split(';');
    const eq = pair.indexOf('=');
    jar[pair.slice(0, eq)] = pair.slice(eq + 1);
  }
  const body = await res.json().catch(() => ({}));
  return { status: res.status, jar, body };
}

function headers(jar, json = false) {
  const h = {
    Cookie: Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '),
    'X-CSRF-Token': jar['cc_csrf'] || '',
  };
  if (json) h['Content-Type'] = 'application/json';
  return h;
}

async function groups(jar) {
  const res = await fetch(`${API}/thesis/groups?roundId=${ROUND_ID}`, { headers: headers(jar) });
  const body = await res.json().catch(() => []);
  const arr = Array.isArray(body) ? body : body.content || body.groups || [];
  return { status: res.status, arr };
}

const TRIO = ['Tiến Sơn', 'Minh Quân', 'Thu Hằng'];

(async () => {
  console.log(`Smoke: ${BASE}`);

  // -- Auth: every demo account logs in --------------------------------------
  const sessions = {};
  for (const [key, email] of Object.entries({
    student: 'student@campuscore.edu',
    student2: 'student2@campuscore.edu',
    student3: 'student3@campuscore.edu',
    lecturer: 'lecturer@campuscore.edu',
    admin: 'admin@campuscore.edu',
  })) {
    const r = await login(email);
    sessions[key] = r.jar;
    check(`login ${email}`, r.status === 200 && r.jar.cc_access_token, `status=${r.status}`);
  }

  // -- Thesis trio: leader + both members see the same roster ----------------
  for (const key of ['student', 'student2', 'student3']) {
    if (!sessions[key].cc_access_token) continue;
    const { status, arr } = await groups(sessions[key]);
    const trio = arr.find(g => (g.members || []).length === 3);
    const names = (trio?.members || []).map(m => `${m.displayName || ''}`);
    const leaderOk = (trio?.members || []).some(m => m.isLeader && `${m.displayName}`.includes('Sơn'));
    check(`thesis trio visible for ${key}`, status === 200 && TRIO.every(n => names.join(' ').includes(n)) && leaderOk,
      `status=${status} groups=${arr.length}`);
  }

  // -- Lecturer: sees the trio as a pending supervised group ------------------
  if (sessions.lecturer.cc_access_token) {
    const { status, arr } = await groups(sessions.lecturer);
    const pending = arr.find(g => g.approvalStatus === 'PENDING' && (g.members || []).length === 3);
    check('lecturer sees pending trio group', status === 200 && !!pending, `status=${status}`);
  }

  // -- Curriculum: student plan resolves --------------------------------------
  if (sessions.student.cc_access_token) {
    const res = await fetch(`${API}/me/curriculum`, { headers: headers(sessions.student) });
    const j = await res.json().catch(() => ({}));
    const items = j.items || j.courses || j.plan || [];
    check('curriculum plan loads', res.status === 200 && items.length > 0, `status=${res.status} items=${items.length}`);
  }

  // -- Assistant: personal-context answer -------------------------------------
  if (sessions.student.cc_access_token) {
    const res = await fetch(`${API}/assistant/chat`, {
      method: 'POST',
      headers: headers(sessions.student, true),
      body: JSON.stringify({
        message: 'Tôi đã đăng ký được bao nhiêu tín chỉ kỳ này?',
        locale: 'vi',
        clientRequestId: crypto.randomUUID(),
      }),
    });
    const j = await res.json().catch(() => ({}));
    const answer = `${j.answer || j.message || j.content || ''}`;
    const grounded = /\d+\s*tín chỉ|tín chỉ|\d+/.test(answer);
    const degraded = /chưa tìm thấy|không tìm thấy|not found in|không có thông tin/i.test(answer);
    // 200 + non-empty is the hard gate; grounded-vs-degraded is reported as
    // context because local stacks legitimately run the lexical fallback when
    // no provider key is configured.
    check('assistant responds', res.status === 200 && answer.length > 10,
      `status=${res.status} answer="${answer.slice(0, 80)}"`);
    if (res.status === 200) {
      console.log(`      assistant mode: ${grounded && !degraded ? 'grounded (personal context)' : degraded ? 'degraded lexical fallback' : 'unclassified'}`);
    }
  }

  // -- Admin: new demo accounts are searchable ---------------------------------
  if (sessions.admin.cc_access_token) {
    for (const q of ['student2', 'student3']) {
      const res = await fetch(`${API}/users?search=${q}`, { headers: headers(sessions.admin) });
      const j = await res.json().catch(() => ({}));
      const arr = j.content || j.users || j.data || (Array.isArray(j) ? j : []);
      check(`admin finds ${q}`, res.status === 200 && arr.length > 0, `status=${res.status}`);
    }
  }

  // -- Opt-in mutation probe (never run by default on prod) --------------------
  if (process.env.SMOKE_MUTATE === '1' && sessions.student.cc_access_token) {
    console.log('SMOKE_MUTATE=1: duplicate-course guard probe...');
    const dup = await fetch(`${API}/enrollments`, {
      method: 'POST',
      headers: headers(sessions.student, true),
      body: JSON.stringify({ sectionId: 'section-auto-013' }),
    });
    check('duplicate enrollment rejected', dup.status === 409, `status=${dup.status}`);
  }

  console.log(failures === 0 ? 'SMOKE PASS' : `SMOKE FAIL (${failures} failing check${failures > 1 ? 's' : ''})`);
  process.exitCode = failures === 0 ? 0 : 1;
})();
