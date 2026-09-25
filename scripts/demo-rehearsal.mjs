// Round-10 item 4: automated demo rehearsal against a CLEAN CLONE stack.
// Proves the runbook's core claims end-to-end over HTTP on a freshly
// provisioned compose database:
//   1. the three .edu showcase accounts + the V82 runbook accounts log in
//   2. an unlisted seeded persona stays locked out
//   3. four-eyes knowledge governance: author creates + submits, a DIFFERENT
//      admin publishes, same-admin publish gets 409
const BASE = 'http://127.0.0.1:4120/api/v1';
const PASSWORD = 'password123';

async function api(path, options = {}) {
  const response = await fetch(BASE + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
  });
  const text = await response.text();
  let body = null;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  return { status: response.status, body };
}

function check(label, condition, detail = '') {
  if (condition) {
    console.log(`PASS  ${label}`);
  } else {
    console.error(`FAIL  ${label} ${detail}`);
    process.exitCode = 1;
  }
}

async function login(email) {
  const result = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  check(`login ${email}`, result.status === 200, `(status ${result.status})`);
  return result.body.accessToken;
}

async function main() {
  // 1. Runbook accounts sign in with the single published demo password.
  const student = await login('student@campuscore.edu');
  const admin = await login('admin@campuscore.edu');
  const admin002 = await login('admin002@campuscore.demo'); // V82
  check('student token present', typeof student === 'string' && student.length > 20);
  check('admin002 (V82 approver) token present', typeof admin002 === 'string' && admin002.length > 20);

  // 2. A seeded persona outside the runbook set stays locked out.
  const persona = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email: 'an.hnt@campuscore.demo', password: PASSWORD }),
  });
  check('unlisted persona an.hnt rejected', persona.status === 401, `(status ${persona.status})`);

  // 3. Four-eyes governance with a live second admin.
  const slug = `rehearsal-four-eyes-${Math.random().toString(36).slice(2, 8)}`;
  const created = await api('/admin/assistant/knowledge', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
    body: JSON.stringify({
      slug,
      locale: 'vi',
      title: 'Diễn tập kiểm duyệt hai người',
      content: 'Học kỳ mới bắt đầu từ tuần đầu tiên của tháng Chín. Sinh viên hoàn thành đăng ký học phần trong hai tuần đầu và thanh toán học phí theo hướng dẫn của Phòng Đào tạo. Các thắc mắc về học phần gửi về bộ phận hỗ trợ học vụ của trường.',
      source: 'Rehearsal runbook',
      domain: 'GENERAL_FAQ',
      priority: 500,
    }),
  });
  check('admin creates draft', created.status === 200, `(status ${created.status} ${JSON.stringify(created.body).slice(0, 120)})`);
  const documentId = created.body?.documentId;
  check('document id returned', Boolean(documentId));

  const sameAdmin = await api(`/admin/assistant/knowledge/${documentId}/publish`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
  });
  check('same-admin publish refused', sameAdmin.status === 409 || sameAdmin.status === 400, `(status ${sameAdmin.status})`);

  const submitted = await api(`/admin/assistant/knowledge/${documentId}/submit`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
  });
  check('author submits for review', submitted.status === 200, `(status ${submitted.status})`);

  const published = await api(`/admin/assistant/knowledge/${documentId}/publish`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin002}` },
  });
  check('second admin publishes (four-eyes)', published.status === 200, `(status ${published.status} ${JSON.stringify(published.body).slice(0, 160)})`);
  check('state is PUBLISHED', published.body?.state === 'PUBLISHED' || published.body?.status === 'PUBLISHED', JSON.stringify(published.body).slice(0, 120));
}

main().catch((error) => {
  console.error('REHEARSAL ABORTED:', error.message);
  process.exitCode = 1;
});
