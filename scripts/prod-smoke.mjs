// Production smoke: read-only end-to-end verification against a deployed
// environment. Run after every deploy:
//   node scripts/prod-smoke.mjs                      # prod (www.campusute.io.vn)
//   BASE_URL=http://localhost:3100 node scripts/prod-smoke.mjs   # local stack
// Covers: demo-account logins, four-member thesis roster, curriculum plan,
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

const GROUP_ID = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93';
const TEAM = [
  { key: 'student', email: 'student@campuscore.edu', userId: 'student-user', profileId: 'student-profile', studentNumber: '24110054', name: 'Nguyễn Tiến Sơn', leader: true, order: 1 },
  { key: 'student2', email: 'student2@campuscore.edu', userId: 'student-user-2', profileId: 'student-profile-058', studentNumber: '24110345', name: 'Huỳnh Hồng Thủy', leader: false, order: 2 },
  { key: 'student3', email: 'student3@campuscore.edu', userId: 'student-user-3', profileId: 'student-profile-059', studentNumber: '24110051', name: 'Trần Thị Ngọc Quyên', leader: false, order: 3 },
  { key: 'student4', email: 'student4@campuscore.edu', userId: 'student-user-4', profileId: 'student-profile-team-004', studentNumber: '24162050', name: 'Vũ Quốc Huy', leader: false, order: 4 },
];

function matchesTeamGroup(group) {
  const roster = group?.members || [];
  return group?.id === GROUP_ID && group?.leaderStudentId === 'student-profile'
    && roster.length === TEAM.length
    && roster.filter(member => member.isLeader).length === 1
    && TEAM.every(expected => roster.some(member => member.studentId === expected.profileId
      && member.studentNumber === expected.studentNumber && member.displayName === expected.name
      && member.memberOrder === expected.order && member.isLeader === expected.leader
      && member.isExternal === false && member.contact === expected.email));
}

(async () => {
  console.log(`Smoke: ${BASE}`);

  // -- Auth: every demo account logs in with its expected role/profile -------
  const STAFF = {
    lecturer: { email: 'lecturer@campuscore.edu', roles: ['LECTURER'], requiresLecturerId: true },
    truongbomon: { email: 'truongbomon@campuscore.demo', roles: ['LECTURER', 'TRUONG_BO_MON'], requiresLecturerId: true },
    truongkhoa: { email: 'truongkhoa@campuscore.demo', roles: ['LECTURER', 'TRUONG_KHOA'], requiresLecturerId: true },
    admin: { email: 'admin@campuscore.edu', roles: ['ADMIN'], requiresLecturerId: false },
    admin002: { email: 'admin002@campuscore.demo', roles: ['ADMIN'], requiresLecturerId: false },
  };
  const sessions = {};
  for (const [key, email] of Object.entries({
    student: 'student@campuscore.edu',
    student2: 'student2@campuscore.edu',
    student3: 'student3@campuscore.edu',
    student4: 'student4@campuscore.edu',
    lecturer: 'lecturer@campuscore.edu',
    truongbomon: 'truongbomon@campuscore.demo',
    truongkhoa: 'truongkhoa@campuscore.demo',
    admin: 'admin@campuscore.edu',
    admin002: 'admin002@campuscore.demo',
  })) {
    const r = await login(email);
    sessions[key] = r.jar;
    const expectedStudent = TEAM.find(member => member.key === key);
    const expectedStaff = STAFF[key];
    const user = r.body.user;
    const actualRoles = [...(user?.roles || [])].sort().join(',');
    const identityMatches = expectedStudent
      ? (user?.id === expectedStudent.userId
        && user.email === expectedStudent.email
        && `${user.lastName} ${user.firstName}`.trim() === expectedStudent.name
        && user.status === 'ACTIVE'
        && actualRoles === 'STUDENT')
      : Boolean(expectedStaff
        && user?.email === expectedStaff.email
        && user.status === 'ACTIVE'
        && actualRoles === [...expectedStaff.roles].sort().join(',')
        && (!expectedStaff.requiresLecturerId || (typeof user.lecturerId === 'string' && user.lecturerId.length > 0)));
    check(`login ${key}`, r.status === 200 && r.jar.cc_access_token && identityMatches, `status=${r.status} roles=${actualRoles}`);
  }

  // -- Thesis team: leader + all members see the same roster -----------------
  for (const { key } of TEAM) {
    if (!sessions[key].cc_access_token) continue;
    const { status, arr } = await groups(sessions[key]);
    check(`thesis team visible for ${key}`, status === 200 && arr.some(matchesTeamGroup),
      `status=${status} groups=${arr.length}`);
  }

  // -- Lecturer: sees the team as an active supervised group -----------------
  if (sessions.lecturer.cc_access_token) {
    const { status, arr } = await groups(sessions.lecturer);
    const supervised = arr.find(group => (group.approvalStatus === 'PENDING' || group.approvalStatus === 'APPROVED') && matchesTeamGroup(group));
    check('lecturer sees supervised team group', status === 200 && !!supervised, `status=${status}`);
  }

  // -- Faculty head (Dean): faculty-scoped overview resolves -----------------
  if (sessions.truongkhoa?.cc_access_token) {
    const res = await fetch(`${API}/thesis/dean/overview?roundId=${ROUND_ID}`, { headers: headers(sessions.truongkhoa) });
    const j = await res.json().catch(() => ({}));
    check(
      'dean overview resolves faculty scope',
      res.status === 200 && j.faculty?.facultyId === 'faculty-demo' && Array.isArray(j.groups),
      `status=${res.status} faculty=${j.faculty?.facultyId || ''}`,
    );
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
    for (const member of TEAM.slice(1)) {
      const q = member.email.split('@')[0];
      const res = await fetch(`${API}/users?search=${q}`, { headers: headers(sessions.admin) });
      const j = await res.json().catch(() => ({}));
      const arr = j.content || j.users || j.data || (Array.isArray(j) ? j : []);
      check(`admin finds ${member.key}`, res.status === 200 && arr.some(user => user.id === member.userId), `status=${res.status}`);
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
