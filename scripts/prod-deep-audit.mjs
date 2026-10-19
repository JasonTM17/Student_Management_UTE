// Deep production audit v2: correct params, paginate at limit=100, scan every
// JSON value for test markers / nulls / data-quality anomalies.
const API = 'https://campuscore-backend-p4em.onrender.com/api/v1';
const PASSWORD = process.env.DEMO_PASSWORD || 'password123';
const ROUND = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60';

const BAD = [
  /\btest\b/i, /e2e/i, /rehearsal/i, /wukong/i, /probe/i, /\baudit\b/i,
  /campuscore\.test/i, /AUTO-/i, /55555555-/i, /\bundefined\b/i,
  /Chưa phân công/i, /\bdummy\b/i, /\bsample\b/i, /\bfake\b/i,
  /\blorem\b/i, /\bTBD\b/i, /\bxxxx\b/i,
];
const OK_KEYS = new Set(['description', 'descriptionEn', 'descriptionVi', 'message', 'body', 'content',
  'note', 'reason', 'detail', 'details', 'answer', 'basisNote', 'action', 'targetType', 'entityName',
  'semesterName', 'semesterNameEn', 'semesterNameVi', 'ipAddress', 'userAgent', 'targetId']);

const issues = [];
let requestCount = 0;

function scan(node, path, label) {
  if (node == null) return;
  if (typeof node === 'string') {
    const key = path.split('.').pop()?.replace(/\[\d+\]/, '') || '';
    if (OK_KEYS.has(key)) return;
    for (const re of BAD) {
      if (re.test(node)) {
        issues.push(`${label} :: ${path} = "${node.slice(0, 140)}"`);
        break;
      }
    }
    return;
  }
  if (Array.isArray(node)) { node.forEach((v, i) => scan(v, `${path}[${i}]`, label)); return; }
  if (typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) {
      if (v === null && /^(name|nameEn|nameVi|label|lecturer|lecturerName|lecturerId|classroom|classroomId|title|code|dean|chair|supervisor|actorLabel|email|phone|building|studentName|studentId|employeeId|courseCode|courseName|sectionNumber)$/.test(k)) {
        issues.push(`${label} :: ${path}.${k} = null`);
      }
      scan(v, `${path}.${k}`, label);
    }
  }
}

async function login(email) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  const jar = {};
  for (const c of res.headers.getSetCookie?.() || []) {
    const [p] = c.split(';'); const i = p.indexOf('=');
    jar[p.slice(0, i)] = p.slice(i + 1);
  }
  return { status: res.status, jar };
}
const H = (j) => ({
  Cookie: Object.entries(j).map(([k, v]) => `${k}=${v}`).join('; '),
  'X-CSRF-Token': j.cc_csrf || '',
});

async function get(jar, path, label, { doScan = true, quiet = false } = {}) {
  requestCount++;
  try {
    const res = await fetch(`${API}${path}`, { headers: H(jar) });
    const j = await res.json().catch(() => null);
    if (res.status !== 200) {
      issues.push(`${label} ${path} -> HTTP ${res.status} ${JSON.stringify(j).slice(0, 140)}`);
      console.log(`ERR ${label} ${path} -> ${res.status}`);
    } else {
      const n = j?.meta?.total ?? j?.data?.length ?? (Array.isArray(j) ? j.length : j?.items?.length ?? '');
      if (!quiet) console.log(`OK  ${label} ${path} -> 200 n=${n}`);
      if (doScan && j) scan(j, '$', `${label} ${path}`);
    }
    return { status: res.status, body: j };
  } catch (e) {
    issues.push(`${label} ${path} -> ${e.message}`);
    return { status: 0 };
  }
}

async function getAll(jar, base, label) {
  // paginate with limit=100 until exhausted (cap 10 pages)
  const all = [];
  for (let page = 1; page <= 10; page++) {
    const sep = base.includes('?') ? '&' : '?';
    const { status, body } = await get(jar, `${base}${sep}page=${page}&limit=100`, label, { quiet: true });
    if (status !== 200) break;
    const rows = body?.data || (Array.isArray(body) ? body : body?.content || []);
    all.push(...rows);
    const total = body?.meta?.total ?? rows.length;
    if (all.length >= total || rows.length === 0) break;
  }
  console.log(`OK* ${label} ${base} -> ${all.length} rows`);
  scan(all, '$', `${label} ${base}`);
  return all;
}

(async () => {
  const jars = {};
  for (const [k, email] of Object.entries({
    student: 'student@campuscore.edu', student2: 'student2@campuscore.edu',
    student3: 'student3@campuscore.edu', student4: 'student4@campuscore.edu',
    lecturer: 'lecturer@campuscore.edu', truongbomon: 'truongbomon@campuscore.demo',
    truongkhoa: 'truongkhoa@campuscore.demo', admin: 'admin@campuscore.edu',
    admin002: 'admin002@campuscore.demo',
  })) {
    const r = await login(email);
    jars[k] = r.jar;
    if (r.status !== 200) issues.push(`login ${k} -> ${r.status}`);
  }
  const admin = jars.admin;

  // ---- admin catalogs (full pagination) ----
  const sections = await getAll(admin, '/sections', 'ADMIN');
  await getAll(admin, '/users', 'ADMIN');
  const courses = await getAll(admin, '/courses', 'ADMIN');
  await getAll(admin, '/departments', 'ADMIN');
  await getAll(admin, '/faculties', 'ADMIN');
  await getAll(admin, '/semesters', 'ADMIN');
  await getAll(admin, '/academic-years', 'ADMIN');
  await getAll(admin, '/classrooms', 'ADMIN');
  await getAll(admin, '/enrollments', 'ADMIN');
  await getAll(admin, '/admin/audit', 'ADMIN');
  await getAll(admin, '/announcements', 'ADMIN');
  await getAll(admin, '/notifications', 'ADMIN');
  await getAll(admin, '/admin/registration/credit-limit-applications', 'ADMIN');
  await getAll(admin, `/thesis/councils?roundId=${ROUND}`, 'ADMIN');
  await getAll(admin, `/thesis/topics?roundId=${ROUND}`, 'ADMIN');
  await getAll(admin, `/thesis/groups?roundId=${ROUND}`, 'ADMIN');

  // section data-quality checks
  let nullLecturer = 0, noSchedule = 0;
  for (const s of sections) {
    if (!s.lecturerId && !s.lecturerName) { nullLecturer++; issues.push(`section ${s.id} (${s.courseCode}-${s.sectionNumber}) missing lecturer`); }
    if (!s.classroomId && !(s.schedules?.length)) { noSchedule++; issues.push(`section ${s.id} (${s.courseCode}-${s.sectionNumber}) no classroom/schedule`); }
  }
  console.log(`SECTIONS ${sections.length} nullLecturer=${nullLecturer} noSchedule=${noSchedule}`);

  // course name coverage
  for (const c of courses) {
    if (!c.nameVi) issues.push(`course ${c.code} missing nameVi`);
    if (!c.nameEn) issues.push(`course ${c.code} missing nameEn`);
  }

  // ---- cross-reference consistency ----
  const enrollments = await getAll(admin, '/enrollments?status=ENROLLED', 'ADMIN-ENR');
  // Offset pagination without a unique sort key re-shuffles between page
  // fetches, so the same row can land on several pages. Track it separately
  // from true duplicate rows — overlap is a server-side ordering defect, not
  // duplicated data.
  const seenEnr = new Set();
  const overlapIds = new Set();
  const uniqEnrollments = [];
  for (const e of enrollments) {
    if (seenEnr.has(e.id)) overlapIds.add(e.id);
    else { seenEnr.add(e.id); uniqEnrollments.push(e); }
  }
  if (overlapIds.size > 0) issues.push(`enrollment pagination overlap: ${overlapIds.size} ids repeated across pages (nondeterministic ORDER BY)`);
  console.log(`PAGE-OVERLAP enrollment ids repeated=${overlapIds.size} fetched=${enrollments.length} unique=${uniqEnrollments.length}`);
  const secById = new Map(sections.map(s => [s.id, s]));
  // enrolledCount vs actual ENROLLED rows
  const cntBySection = {};
  for (const e of uniqEnrollments) {
    if (e.sectionId) cntBySection[e.sectionId] = (cntBySection[e.sectionId] || 0) + 1;
    if (e.sectionId && !secById.has(e.sectionId)) issues.push(`enrollment ${e.id} -> orphan sectionId ${e.sectionId}`);
    if (e.studentId == null && e.student?.id == null) issues.push(`enrollment ${e.id} missing student ref`);
  }
  let drift = 0;
  for (const s of sections) {
    const actual = cntBySection[s.id] || 0;
    const stored = s.enrolledCount ?? 0;
    if (stored !== actual && s.capacity && s.capacity > 0) {
      drift++;
      if (drift <= 20) issues.push(`section ${s.id} (${s.courseCode}-${s.sectionNumber}) enrolledCount=${stored} actual=${actual}`);
    }
  }
  console.log(`ENROLLED-DRIFT sections_mismatch=${drift} (enrollees rows=${uniqEnrollments.length})`);
  // duplicate enrollments (same student+section active)
  const dup = {};
  for (const e of uniqEnrollments) {
    const k = `${e.student?.id ?? e.studentId}|${e.sectionId}`;
    dup[k] = (dup[k] || 0) + 1;
  }
  const dups = Object.entries(dup).filter(([, n]) => n > 1);
  for (const [k, n] of dups.slice(0, 20)) issues.push(`duplicate ENROLLED ${k} x${n}`);
  console.log(`DUP-ENROLL ${dups.length}`);
  // capacity violations
  for (const s of sections) {
    const actual = cntBySection[s.id] || 0;
    if (s.capacity && actual > s.capacity) issues.push(`section ${s.id} (${s.courseCode}-${s.sectionNumber}) over capacity ${actual}/${s.capacity}`);
  }

  // ---- student surfaces ----
  for (const s of ['student', 'student2', 'student3', 'student4']) {
    for (const p of ['/auth/me', '/me/curriculum', '/enrollments/my', '/enrollments/my/grades',
      '/enrollments/my/transcript', '/attendance/my', '/attendance/my/summary', '/conduct/my',
      '/thesis/groups/mine', `/thesis/me/progress?roundId=${ROUND}`, `/thesis/me/results?roundId=${ROUND}`,
      '/registration/rounds', '/me/registration/summary']) {
      await get(jars[s], p, s, { quiet: false });
    }
    await getAll(jars[s], '/announcements/my', s);
    await getAll(jars[s], '/notifications/my', s);
  }

  // ---- lecturer / tbm / tk ----
  for (const p of ['/auth/me', '/sections/my/schedule', '/sections/my/grading', '/thesis/me/workload',
    `/thesis/councils?roundId=${ROUND}`, `/thesis/topics?roundId=${ROUND}`]) {
    await get(jars.lecturer, p, 'LECTURER');
  }
  for (const role of ['truongbomon', 'truongkhoa']) {
    for (const p of ['/auth/me', '/thesis/me/workload', `/thesis/councils?roundId=${ROUND}`, '/sections/my/schedule']) {
      await get(jars[role], p, role);
    }
  }
  await get(jars.truongkhoa, `/thesis/dean/overview?roundId=${ROUND}`, 'truongkhoa');

  const uniq = [...new Set(issues)];
  console.log(`\n===== AUDIT DONE: ${requestCount} requests, ${uniq.length} flagged =====`);
  const { writeFileSync } = await import('node:fs');
  writeFileSync('scripts/audit-flags.txt', uniq.join('\n'));
  // category summary
  const cats = {};
  for (const i of uniq) {
    const cat = i.replace(/\[\d+\]/g, '[N]').replace(/-auto-\d+/g, '-auto-N').replace(/"[^"]*"/g, '"STR"').replace(/:: \$\..* =/, ':: FIELD =');
    cats[cat] = (cats[cat] || 0) + 1;
  }
  Object.entries(cats).sort((a, b) => b[1] - a[1]).forEach(([c, n]) => console.log(`${n}\t${c}`));
})();
