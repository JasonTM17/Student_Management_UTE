// Wait for Render to serve the section-search fix, then verify prod searches.
const API = 'https://campuscore-backend-p4em.onrender.com/api/v1';
const PASSWORD = process.env.DEMO_PASSWORD || 'password123';

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
  return { status: res.status, jar };
}

const headers = (jar) => ({
  Cookie: Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '),
  'X-CSRF-Token': jar.cc_csrf || '',
});

(async () => {
  const { status, jar } = await login('admin@campuscore.edu');
  console.log(`login admin -> ${status}`);
  if (status !== 200) process.exit(1);

  const deadline = Date.now() + 12 * 60 * 1000; // up to 12 min for Render swap
  let live = false;
  while (Date.now() < deadline) {
    const res = await fetch(`${API}/sections?search=SE401`, { headers: headers(jar) });
    if (res.status === 200) {
      const j = await res.json();
      console.log(`LIVE: /sections?search=SE401 -> 200 total=${j.meta?.total}`);
      live = true;
      break;
    }
    console.log(`waiting... /sections?search=SE401 -> ${res.status}`);
    await new Promise(r => setTimeout(r, 20_000));
  }
  if (!live) {
    console.log('TIMEOUT: section search still failing after 12 min');
    process.exit(1);
  }

  const checks = [
    ['/sections?search=SE401', null],
    ['/sections?search=' + encodeURIComponent('Hoàng Thái Xuân Khoa'), null],
    ['/sections?search=' + encodeURIComponent('Java nâng cao'), null],
    ['/sections?search=' + encodeURIComponent('khong co ket qua nay'), 0],
    ['/enrollments?search=' + encodeURIComponent('Nguyễn Tiến Sơn'), null],
    ['/enrollments?search=24162050', null],
  ];
  for (const [path, expected] of checks) {
    const res = await fetch(`${API}${path}`, { headers: headers(jar) });
    const j = await res.json().catch(() => ({}));
    const total = j.meta?.total ?? j.data?.length ?? '?';
    console.log(`${res.status === 200 ? 'OK ' : 'ERR'} ${path} -> ${res.status} total=${total}`);
    if (res.status !== 200) process.exitCode = 1;
    if (expected !== null && total !== expected) process.exitCode = 1;
  }
})();
