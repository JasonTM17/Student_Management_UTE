const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath, stubs = {}) {
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  const resolve = (specifier) =>
    Object.prototype.hasOwnProperty.call(stubs, specifier) ? stubs[specifier] : require(specifier);
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, resolve);
  return moduleRecord.exports;
}

function loadTsx(relativePath, stubs) {
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
    },
  }).outputText;
  const moduleRecord = { exports: {} };
  const resolve = (specifier) =>
    Object.prototype.hasOwnProperty.call(stubs, specifier) ? stubs[specifier] : require(specifier);
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, resolve);
  return moduleRecord.exports;
}

const enrollmentStatus = loadTs('src/lib/enrollment-status.ts');
const { deriveStudentDashboardMetrics } = loadTs('src/lib/student-dashboard-metrics.ts', {
  '@/lib/enrollment-status': enrollmentStatus,
});
const icon = () => null;
const { StudentDashboardCreditProgress, StudentDashboardMetricCards } = loadTsx(
  'src/components/dashboard/StudentDashboardMetrics.tsx',
  {
    'lucide-react': new Proxy({}, { get: () => icon }),
    '@/components/LocalizedLink': {
      LocalizedLink: ({ href, title, children }) =>
        React.createElement('a', { href, title }, children),
    },
    '@/components/dashboard/WorkspaceSurface': {
      WorkspacePanel: ({ title, children }) =>
        React.createElement('section', null, React.createElement('h2', null, title), children),
    },
    '@/lib/utils': { cn: (...values) => values.filter(Boolean).join(' ') },
  },
);

const fixture = [
  { id: 'enrolled-1', status: 'ENROLLED', section: { course: { credits: 3 } } },
  { id: 'pending-1', status: 'PENDING', section: { course: { credits: 4 } } },
  { id: 'dropped-1', status: 'DROPPED', section: { course: { credits: 6 } } },
  {
    id: 'cancelled-1',
    status: 'ENROLLED',
    section: { status: 'CANCELLED', course: { credits: 5 } },
  },
  { id: 'completed-1', status: 'COMPLETED', section: { course: { credits: 3 } } },
];

test('student dashboard renders active and pending totals with current credits and configured cap', () => {
  const metrics = deriveStudentDashboardMetrics(fixture, 28);
  const formatNumber = (value) => String(value);
  const chips = React.createElement(StudentDashboardMetricCards, {
    chips: [
      {
        label: 'Courses',
        value: formatNumber(metrics.activeCourses.length),
        icon,
        tone: '',
        href: '#',
        tooltip: '',
        wraps: false,
      },
      {
        label: 'Active',
        value: formatNumber(metrics.enrolledCourseCount),
        icon,
        tone: '',
        href: '#',
        tooltip: '',
        wraps: false,
      },
      {
        label: 'Pending',
        value: formatNumber(metrics.pendingCourses.length),
        icon,
        tone: '',
        href: '#',
        tooltip: '',
        wraps: false,
      },
      {
        label: 'Term',
        value: 'Fall 2026',
        icon,
        tone: '',
        href: '#',
        tooltip: '',
        wraps: true,
      },
    ],
  });
  const progress = React.createElement(StudentDashboardCreditProgress, {
    metrics,
    formatNumber,
    copy: {
      title: 'Term progress',
      creditLabel: 'Current credits',
      creditsEarnedOnly: 'Earned credits only',
      progressStatus: (percent) => `${percent}% used`,
      creditsAvailable: (credits) => `${credits} credits available`,
      creditsCapped: 'Credit cap reached',
      pendingBadge: (count) => `${count} pending`,
    },
  });
  const html = renderToStaticMarkup(React.createElement(React.Fragment, null, chips, progress));
  const renderedText = html.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ');

  assert.match(renderedText, /2 Courses/);
  assert.match(renderedText, /1 Active/);
  assert.match(renderedText, /1 Pending/);
  assert.match(renderedText, /Fall 2026 Term/);
  assert.match(renderedText, /7 \/ 28/);
  assert.match(renderedText, /25% used/);
  assert.match(renderedText, /21 credits available/);
  assert.match(renderedText, /1 pending/);

  const dashboardPage = read('src/app/dashboard/page.tsx');
  assert.match(dashboardPage, /deriveStudentDashboardMetrics\(enrollments, termCreditCap\)/);
  assert.match(dashboardPage, /<StudentDashboardMetricCards/);
  assert.match(dashboardPage, /chips=\{statChips\}/);
  assert.match(dashboardPage, /<StudentDashboardCreditProgress/);
});
