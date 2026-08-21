import crypto from 'node:crypto';
import http from 'node:http';
import { once } from 'node:events';

const FIXTURE = {
  invoices: {
    old: {
      id: 'invoice-old',
      invoiceNumber: 'INV-001',
      studentId: 'student-1',
      studentUserId: 'user-student-1',
      studentDisplayName: 'Student 1',
      studentEmail: 'student-1@campuscore.edu',
      studentCode: 'STUDENT-1',
      semesterId: 'semester-1',
      semesterName: 'Fall 2026',
      semesterNameEn: 'Fall 2026',
      semesterNameVi: 'Học kỳ Thu 2026',
      status: 'PENDING',
      subtotal: 1000,
      discount: 0,
      total: 1000,
      dueDate: '2027-08-20T00:00:00.000Z',
      paidAt: null,
      notes: 'Seeded invoice',
      createdAt: '2026-08-20T00:00:00.000Z',
      updatedAt: '2026-08-20T00:00:00.000Z',
      paidAmount: 300,
      balance: 700,
      student: {
        user: {
          firstName: 'Student',
          lastName: '1',
          email: 'student-1@campuscore.edu',
        },
        studentId: 'STUDENT-1',
      },
      semester: {
        name: 'Fall 2026',
        nameEn: 'Fall 2026',
        nameVi: 'Học kỳ Thu 2026',
      },
      items: [
        {
          id: 'item-invoice-old',
          invoiceId: 'invoice-old',
          description: 'Tuition',
          quantity: 1,
          unitPrice: 1000,
          total: 1000,
        },
      ],
      payments: [
        {
          id: 'payment-old',
          paymentNumber: 'PAY-001',
          invoiceId: 'invoice-old',
          studentId: 'student-1',
          amount: 300,
          method: 'CARD',
          status: 'COMPLETED',
          paidAt: '2026-08-19T23:30:00.000Z',
          transactionId: 'txn-payment-old',
          paymentIntentId: null,
          notes: 'Seeded payment',
          createdAt: '2026-08-19T23:30:00.000Z',
          updatedAt: '2026-08-19T23:30:00.000Z',
        },
      ],
    },
    new: {
      id: 'invoice-new',
      invoiceNumber: 'INV-002',
      studentId: 'student-2',
      studentUserId: 'user-student-2',
      studentDisplayName: 'Student 2',
      studentEmail: 'student-2@campuscore.edu',
      studentCode: 'STUDENT-2',
      semesterId: 'semester-1',
      semesterName: 'Fall 2026',
      semesterNameEn: 'Fall 2026',
      semesterNameVi: 'Học kỳ Thu 2026',
      status: 'DRAFT',
      subtotal: 1000,
      discount: 0,
      total: 1000,
      dueDate: '2027-08-20T00:00:00.000Z',
      paidAt: null,
      notes: 'Seeded invoice',
      createdAt: '2026-08-20T00:01:00.000Z',
      updatedAt: '2026-08-20T00:01:00.000Z',
      paidAmount: 0,
      balance: 1000,
      student: {
        user: {
          firstName: 'Student',
          lastName: '2',
          email: 'student-2@campuscore.edu',
        },
        studentId: 'STUDENT-2',
      },
      semester: {
        name: 'Fall 2026',
        nameEn: 'Fall 2026',
        nameVi: 'Học kỳ Thu 2026',
      },
      items: [
        {
          id: 'item-invoice-new',
          invoiceId: 'invoice-new',
          description: 'Tuition',
          quantity: 1,
          unitPrice: 1000,
          total: 1000,
        },
      ],
      payments: [
        {
          id: 'payment-new',
          paymentNumber: 'PAY-002',
          invoiceId: 'invoice-new',
          studentId: 'student-2',
          amount: 0,
          method: 'CARD',
          status: 'FAILED',
          paidAt: null,
          transactionId: 'txn-payment-new',
          paymentIntentId: null,
          notes: 'Seeded payment',
          createdAt: '2026-08-20T00:02:00.000Z',
          updatedAt: '2026-08-20T00:02:00.000Z',
        },
      ],
    },
  },
};

const corpus = [
  { name: 'admin invoice list page', method: 'GET', path: '/api/v1/finance/invoices?page=1&limit=1', auth: 'admin' },
  { name: 'admin invoice list filtered', method: 'GET', path: '/api/v1/finance/invoices?studentId=student-1', auth: 'admin' },
  { name: 'admin invoice detail', method: 'GET', path: '/api/v1/finance/invoices/invoice-old', auth: 'admin' },
  { name: 'student invoice list', method: 'GET', path: '/api/v1/finance/my/invoices', auth: 'student' },
  { name: 'student invoice detail', method: 'GET', path: '/api/v1/finance/my/invoices/invoice-old', auth: 'student' },
  { name: 'admin payment list page', method: 'GET', path: '/api/v1/finance/payments?page=1&limit=1', auth: 'admin' },
  {
    name: 'admin payment list filtered',
    method: 'GET',
    path: '/api/v1/finance/payments?status=COMPLETED',
    auth: 'admin',
    legacyRestoredSchemaLimitation:
      'Legacy Prisma status filter fails on this restored varchar schema because finance.PaymentStatus enum is absent.',
  },
  { name: 'admin payment detail', method: 'GET', path: '/api/v1/finance/payments/payment-old', auth: 'admin' },
  { name: 'missing invoice detail', method: 'GET', path: '/api/v1/finance/invoices/missing', auth: 'admin' },
  { name: 'missing student invoice detail', method: 'GET', path: '/api/v1/finance/my/invoices/missing', auth: 'student' },
  { name: 'missing payment detail', method: 'GET', path: '/api/v1/finance/payments/missing', auth: 'admin' },
];

const selfTest = process.argv.includes('--self-test');

if (selfTest) {
  await withSelfTestServers(async ({ legacyBaseUrl, javaBaseUrl }) => {
    await runDifferential({
      legacyBaseUrl,
      javaBaseUrl,
      adminToken: 'self-test-admin',
      studentToken: 'self-test-student',
    });
  });
} else {
  const legacyBaseUrl = requiredEnv('FINANCE_DIFF_LEGACY_BASE_URL');
  const javaBaseUrl = requiredEnv('FINANCE_DIFF_JAVA_BASE_URL');
  const jwtSecret = process.env.FINANCE_DIFF_JWT_SECRET ?? requiredEnv('JWT_SECRET');
  const adminToken = process.env.FINANCE_DIFF_ADMIN_TOKEN ?? signJwt(jwtSecret, {
    sub: 'finance-admin-diff',
    email: 'admin@campuscore.edu',
    roles: ['ADMIN'],
    permissions: [],
  });
  const studentToken = process.env.FINANCE_DIFF_STUDENT_TOKEN ?? signJwt(jwtSecret, {
    sub: 'finance-student-diff',
    email: 'student1@campuscore.edu',
    roles: ['STUDENT'],
    permissions: [],
    studentId: 'student-1',
  });

  await runDifferential({
    legacyBaseUrl,
    javaBaseUrl,
    adminToken,
    studentToken,
  });
}

async function runDifferential({ legacyBaseUrl, javaBaseUrl, adminToken, studentToken }) {
  const report = {
    generatedAt: new Date().toISOString(),
    corpus: corpus.map(({ name, method, path, auth, legacyRestoredSchemaLimitation }) => ({
      name,
      method,
      path,
      auth,
      legacyRestoredSchemaLimitation,
    })),
    endpoints: {
      legacy: redactUrl(legacyBaseUrl),
      java: redactUrl(javaBaseUrl),
    },
    comparisons: [],
    routeSequence: [],
    limitations: [],
  };
  const failures = [];

  for (const item of corpus) {
    const legacy = await requestJson(legacyBaseUrl, item, { adminToken, studentToken });
    const java = await requestJson(javaBaseUrl, item, { adminToken, studentToken });
    const comparison = compareResponses(item, legacy, java);
    report.comparisons.push(comparison);
    if (comparison.limitation) {
      report.limitations.push({
        name: item.name,
        limitation: comparison.limitation,
      });
    }
    if (comparison.result !== 'PASS') {
      failures.push(`${item.name}: ${comparison.reason}`);
    }
  }

  for (const owner of ['legacy-before', 'java-candidate', 'legacy-after']) {
    const baseUrl = owner === 'java-candidate' ? javaBaseUrl : legacyBaseUrl;
    const probe = await requestJson(
      baseUrl,
      corpus[0],
      { adminToken, studentToken },
    );
    report.routeSequence.push({
      owner,
      status: probe.status,
      contentType: probe.contentType,
      bodyHash: hashStable(normalizeComparableBody(corpus[0], probe)),
    });
  }

  const firstLegacyHash = report.routeSequence[0].bodyHash;
  const finalLegacyHash = report.routeSequence[2].bodyHash;
  if (firstLegacyHash !== finalLegacyHash) {
    failures.push('legacy rollback sequence changed the legacy response hash');
  }

  if (failures.length > 0) {
    console.error(JSON.stringify({ result: 'FAIL', failures, report }, null, 2));
    process.exitCode = 1;
    return;
  }

  console.log(JSON.stringify({
    result: report.limitations.length > 0 ? 'PASS_WITH_LIMITATIONS' : 'PASS',
    report,
  }, null, 2));
}

function compareResponses(item, legacy, java) {
  const limitation = legacyRestoredSchemaLimitation(item, legacy, java);
  if (limitation) {
    return {
      name: item.name,
      result: 'PASS',
      status: 'LEGACY_RESTORED_SCHEMA_LIMITATION',
      limitation,
      legacyStatus: legacy.status,
      javaStatus: java.status,
      javaBodyHash: hashStable(normalizeComparableBody(item, java)),
    };
  }

  if (legacy.status !== java.status) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `status mismatch legacy=${legacy.status} java=${java.status}`,
      legacy,
      java,
    };
  }

  if (legacy.status >= 400) {
    const legacyError = normalizeErrorResponse(legacy);
    const javaError = normalizeErrorResponse(java);
    if (hashStable(legacyError) !== hashStable(javaError)) {
      return {
        name: item.name,
        result: 'FAIL',
        reason: `error mismatch legacyHash=${hashStable(legacyError)} javaHash=${hashStable(javaError)}`,
        legacy,
        java,
      };
    }
    return {
      name: item.name,
      result: 'PASS',
      status: legacy.status,
      contentType: legacy.contentType,
      bodyHash: hashStable(legacyError),
    };
  }

  if (legacy.contentType !== java.contentType) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `content-type mismatch legacy=${legacy.contentType} java=${java.contentType}`,
      legacy,
      java,
    };
  }

  const legacyHash = hashStable(normalizeComparableBody(item, legacy));
  const javaHash = hashStable(normalizeComparableBody(item, java));
  if (legacyHash !== javaHash) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `body mismatch legacyHash=${legacyHash} javaHash=${javaHash}`,
      legacy,
      java,
    };
  }

  return {
    name: item.name,
    result: 'PASS',
    status: legacy.status,
    contentType: legacy.contentType,
    bodyHash: legacyHash,
  };
}

function normalizeComparableBody(item, response) {
  const body = response.body;
  if (Array.isArray(body)) {
    return body.map(normalizeInvoiceListItem);
  }

  if (!body || typeof body !== 'object') {
    return body;
  }

  if (Array.isArray(body.data) && body.meta && typeof body.meta === 'object') {
    return {
      ...body,
      data: body.data.map((entry) => normalizeCollectionEntry(item, entry)),
    };
  }

  if (isInvoiceDetailBody(body)) {
    const invoiceSummary = normalizeInvoiceSummary(body);
    return {
      ...body,
      payments: Array.isArray(body.payments)
        ? body.payments.map((payment) =>
            normalizePaymentResponse(payment, invoiceSummary),
          )
        : body.payments,
      items: Array.isArray(body.items)
        ? body.items.map(normalizeInvoiceItem)
        : body.items,
    };
  }

  if (isPaymentBody(body)) {
    return normalizePaymentResponse(body);
  }

  return body;
}

function normalizeCollectionEntry(item, entry) {
  if (item.path.startsWith('/api/v1/finance/payments')) {
    return normalizePaymentResponse(entry);
  }
  return normalizeInvoiceListItem(entry);
}

function normalizeErrorResponse(response) {
  const body = response.body && typeof response.body === 'object' ? response.body : {};
  const message = Array.isArray(body.message)
    ? body.message.join(' | ')
    : typeof body.message === 'string'
      ? body.message
      : '';
  return {
    status: response.status,
    message,
    path: typeof body.path === 'string' ? body.path : '',
  };
}

function normalizeInvoiceListItem(item) {
  if (!item || typeof item !== 'object') {
    return item;
  }

  return {
    ...item,
    subtotal: toNumber(item.subtotal),
    discount: toNumber(item.discount),
    total: toNumber(item.total),
    paidAmount: toNumber(item.paidAmount),
    balance: toNumber(item.balance),
    payments: Array.isArray(item.payments)
      ? item.payments.map((payment) => normalizePaymentResponse(payment))
      : item.payments,
    student: normalizeStudentSnapshot(item.student),
    semester: normalizeSemesterSnapshot(item.semester),
  };
}

function normalizeInvoiceDetailBody(body) {
  const invoiceSummary = normalizeInvoiceSummary(body);
  return {
    ...body,
    items: Array.isArray(body.items) ? body.items.map(normalizeInvoiceItem) : body.items,
    payments: Array.isArray(body.payments)
      ? body.payments.map((payment) => normalizePaymentResponse(payment, invoiceSummary))
      : body.payments,
  };
}

function normalizePaymentResponse(payment, fallbackInvoice) {
  if (!payment || typeof payment !== 'object') {
    return payment;
  }

  const invoiceSource = payment.invoice ?? fallbackInvoice;
  return {
    ...payment,
    amount: toNumber(payment.amount),
    invoice: invoiceSource ? normalizeInvoiceSummary(invoiceSource) : invoiceSource,
  };
}

function normalizeInvoiceSummary(source) {
  if (!source || typeof source !== 'object') {
    return source;
  }

  return {
    id: source.id,
    invoiceNumber: source.invoiceNumber,
    studentId: source.studentId,
    studentUserId: source.studentUserId,
    studentDisplayName: source.studentDisplayName,
    studentEmail: source.studentEmail,
    studentCode: source.studentCode,
    semesterId: source.semesterId,
    semesterName: source.semesterName,
    semesterNameEn: source.semesterNameEn ?? source.semesterName,
    semesterNameVi: source.semesterNameVi,
    status: source.status,
    subtotal: toNumber(source.subtotal),
    discount: toNumber(source.discount),
    total: toNumber(source.total),
    dueDate: source.dueDate,
    paidAt: source.paidAt,
    notes: source.notes,
    createdAt: source.createdAt,
    updatedAt: source.updatedAt,
  };
}

function normalizeInvoiceItem(item) {
  if (!item || typeof item !== 'object') {
    return item;
  }

  return {
    ...item,
    unitPrice: toNumber(item.unitPrice),
    total: toNumber(item.total),
  };
}

function normalizeStudentSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') {
    return snapshot;
  }

  return {
    user: snapshot.user ? normalizeUserSnapshot(snapshot.user) : snapshot.user,
    studentId: snapshot.studentId,
  };
}

function normalizeUserSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') {
    return snapshot;
  }

  return {
    firstName: snapshot.firstName,
    lastName: snapshot.lastName,
    email: snapshot.email,
  };
}

function normalizeSemesterSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') {
    return snapshot;
  }

  return {
    name: snapshot.name,
    nameEn: snapshot.nameEn ?? snapshot.name,
    nameVi: snapshot.nameVi,
  };
}

function isInvoiceDetailBody(body) {
  return (
    Object.hasOwn(body, 'items') &&
    Object.hasOwn(body, 'payments') &&
    Object.hasOwn(body, 'invoiceNumber') &&
    Object.hasOwn(body, 'studentId')
  );
}

function isPaymentBody(body) {
  return Object.hasOwn(body, 'paymentNumber') && Object.hasOwn(body, 'invoice') && Object.hasOwn(body, 'studentId');
}

function toNumber(value) {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
}

function legacyRestoredSchemaLimitation(item, legacy, java) {
  if (!item.legacyRestoredSchemaLimitation) {
    return null;
  }
  const error = normalizeErrorResponse(legacy);
  if (
    legacy.status === 500 &&
    java.status >= 200 &&
    java.status < 300 &&
    error.message.includes('PaymentStatus')
  ) {
    return item.legacyRestoredSchemaLimitation;
  }
  return null;
}

function requestJson(baseUrl, item, tokens) {
  return fetch(new URL(item.path, ensureTrailingSlash(baseUrl)), {
    method: item.method,
    headers: {
      authorization: `Bearer ${item.auth === 'student' ? tokens.studentToken : tokens.adminToken}`,
      accept: 'application/json',
    },
  }).then(async (response) => {
    const text = await response.text();
    return {
      status: response.status,
      contentType: normalizeContentType(response.headers.get('content-type')),
      body: parseBody(text),
    };
  });
}

function parseBody(text) {
  if (text.length === 0) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function normalizeContentType(value) {
  return (value ?? '').split(';', 1)[0].trim().toLowerCase();
}

function hashStable(value) {
  return crypto.createHash('sha256').update(stableJson(value)).digest('hex');
}

function stableJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(',')}]`;
  }
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function signJwt(secret, claims) {
  if (secret.length < 32) {
    throw new Error('JWT secret must contain at least 32 characters');
  }

  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64Url(JSON.stringify({
    iat: now,
    exp: now + 300,
    ...claims,
  }));
  const data = `${header}.${payload}`;
  const signature = crypto.createHmac('sha256', secret).update(data).digest('base64url');
  return `${data}.${signature}`;
}

function base64Url(text) {
  return Buffer.from(text).toString('base64url');
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required. Use --self-test for a local harness check.`);
  }
  return value;
}

function ensureTrailingSlash(value) {
  return value.endsWith('/') ? value : `${value}/`;
}

function redactUrl(value) {
  const url = new URL(value);
  url.username = '';
  url.password = '';
  return url.toString();
}

async function withSelfTestServers(callback) {
  const legacy = createSelfTestServer('legacy');
  const java = createSelfTestServer('java');
  try {
    legacy.listen(0, '127.0.0.1');
    java.listen(0, '127.0.0.1');
    await Promise.all([once(legacy, 'listening'), once(java, 'listening')]);
    await callback({
      legacyBaseUrl: `http://127.0.0.1:${legacy.address().port}`,
      javaBaseUrl: `http://127.0.0.1:${java.address().port}`,
    });
  } finally {
    await Promise.all([closeServer(legacy), closeServer(java)]);
  }
}

function createSelfTestServer(label) {
  return http.createServer((request, response) => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const payload = selfTestPayload(label, url);
    response.setHeader('content-type', 'application/json');
    response.setHeader('x-rehearsal-owner', label);
    response.statusCode = payload.status;
    response.end(JSON.stringify(payload.body));
  });
}

function selfTestPayload(label, url) {
  const legacy = label === 'legacy';
  const base = fixtureBodies(legacy);

  if (url.pathname === '/api/v1/finance/invoices' && url.searchParams.get('page') === '1') {
    return { status: 200, body: { data: [base.invoices.new], meta: { total: 2, page: 1, limit: 1, totalPages: 2 } } };
  }
  if (url.pathname === '/api/v1/finance/invoices' && url.searchParams.get('studentId') === 'student-1') {
    return { status: 200, body: { data: [base.invoices.old], meta: { total: 1, page: 1, limit: 20, totalPages: 1 } } };
  }
  if (url.pathname === '/api/v1/finance/invoices/invoice-old') {
    return { status: 200, body: base.invoiceDetailOld };
  }
  if (url.pathname === '/api/v1/finance/my/invoices') {
    return { status: 200, body: [base.studentInvoices.old] };
  }
  if (url.pathname === '/api/v1/finance/my/invoices/invoice-old') {
    return { status: 200, body: base.invoiceDetailOld };
  }
  if (url.pathname === '/api/v1/finance/payments' && url.searchParams.get('page') === '1') {
    return { status: 200, body: { data: [base.payments.new], meta: { total: 2, page: 1, limit: 1, totalPages: 2 } } };
  }
  if (url.pathname === '/api/v1/finance/payments' && url.searchParams.get('status') === 'COMPLETED') {
    return { status: 200, body: { data: [base.payments.old], meta: { total: 1, page: 1, limit: 20, totalPages: 1 } } };
  }
  if (url.pathname === '/api/v1/finance/payments/payment-old') {
    return { status: 200, body: base.paymentDetailOld };
  }
  if (url.pathname === '/api/v1/finance/invoices/missing') {
    return errorResponse(404, 'Invoice not found', url.pathname, legacy);
  }
  if (url.pathname === '/api/v1/finance/my/invoices/missing') {
    return errorResponse(404, 'Invoice not found', url.pathname, legacy);
  }
  if (url.pathname === '/api/v1/finance/payments/missing') {
    return errorResponse(404, 'Payment not found', url.pathname, legacy);
  }
  return { status: 404, body: legacy ? errorBody(404, 'Not Found', url.pathname) : {
    code: 'NOT_FOUND',
    message: 'Resource not found',
    path: url.pathname,
    requestId: 'self-test-request-id',
    timestamp: new Date().toISOString(),
    fields: {},
  } };
}

function fixtureBodies(legacy) {
  const invoices = {
    old: legacy
      ? {
          ...FIXTURE.invoices.old,
        }
      : {
          ...FIXTURE.invoices.old,
        },
    new: legacy
      ? {
          ...FIXTURE.invoices.new,
        }
      : {
          ...FIXTURE.invoices.new,
        },
  };

  const studentInvoice = (invoice) => ({
    id: invoice.id,
    invoiceNumber: invoice.invoiceNumber,
    semesterName: invoice.semesterName,
    semesterNameEn: invoice.semesterNameEn,
    semesterNameVi: invoice.semesterNameVi,
    semesterId: invoice.semesterId,
    status: invoice.status,
    subtotal: invoice.subtotal,
    discount: invoice.discount,
    total: invoice.total,
    dueDate: invoice.dueDate,
    paidAt: invoice.paidAt,
    createdAt: invoice.createdAt,
    paidAmount: invoice.paidAmount,
    balance: invoice.balance,
  });

  const paymentInvoice = (invoice) => ({
    id: invoice.id,
    invoiceNumber: invoice.invoiceNumber,
    studentId: invoice.studentId,
    studentUserId: invoice.studentUserId,
    studentDisplayName: invoice.studentDisplayName,
    studentEmail: invoice.studentEmail,
    studentCode: invoice.studentCode,
    semesterId: invoice.semesterId,
    semesterName: invoice.semesterName,
    semesterNameEn: invoice.semesterNameEn,
    semesterNameVi: invoice.semesterNameVi,
    status: invoice.status,
    subtotal: invoice.subtotal,
    discount: invoice.discount,
    total: invoice.total,
    dueDate: invoice.dueDate,
    paidAt: invoice.paidAt,
    notes: invoice.notes,
    createdAt: invoice.createdAt,
    updatedAt: invoice.updatedAt,
  });

  const studentInvoices = {
    old: studentInvoice(FIXTURE.invoices.old),
    new: studentInvoice(FIXTURE.invoices.new),
  };

  const payments = {
    old: legacy
      ? {
          ...FIXTURE.invoices.old.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.old),
        }
      : {
          ...FIXTURE.invoices.old.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.old),
        },
    new: legacy
      ? {
          ...FIXTURE.invoices.new.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.new),
        }
      : {
          ...FIXTURE.invoices.new.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.new),
        },
  };

  return {
    invoices,
    studentInvoices,
    payments,
    invoiceDetailOld: {
      ...FIXTURE.invoices.old,
      payments: FIXTURE.invoices.old.payments,
    },
    paymentDetailOld: legacy
      ? {
          ...FIXTURE.invoices.old.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.old),
        }
      : {
          ...FIXTURE.invoices.old.payments[0],
          invoice: paymentInvoice(FIXTURE.invoices.old),
        },
  };
}

function errorResponse(status, message, path, legacy) {
  return legacy
    ? {
        status,
        body: errorBody(status, message, path),
      }
    : {
        status,
        body: {
          code: `HTTP_${status}`,
          message,
          path,
          requestId: 'self-test-request-id',
          timestamp: new Date().toISOString(),
          fields: {},
        },
      };
}

function errorBody(status, message, path) {
  return {
    statusCode: status,
    error: status === 404 ? 'Not Found' : 'Bad Request',
    message: [message],
    path,
    timestamp: new Date().toISOString(),
    method: 'GET',
  };
}

async function closeServer(server) {
  if (!server.listening) {
    return;
  }
  server.close();
  await once(server, 'close');
}
