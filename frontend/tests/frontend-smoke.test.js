const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), 'utf8');
}

function readFirstExisting(relPaths) {
  for (const relPath of relPaths) {
    const fullPath = path.join(root, relPath);
    if (fs.existsSync(fullPath)) {
      return fs.readFileSync(fullPath, 'utf8');
    }
  }

  throw new Error(`Could not find any of: ${relPaths.join(', ')}`);
}

function assertPatterns(relPath, patterns) {
  const source = read(relPath);

  for (const pattern of patterns) {
    assert.match(source, pattern, `${relPath} is missing expected pattern: ${pattern}`);
  }
}

function walkFiles(dir, predicate) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkFiles(fullPath, predicate));
      continue;
    }

    if (predicate(fullPath)) {
      files.push(fullPath);
    }
  }

  return files;
}

function getJsxTagName(node) {
  if (!node.tagName) {
    return '';
  }

  if (ts.isIdentifier(node.tagName)) {
    return node.tagName.text;
  }

  if (ts.isPropertyAccessExpression(node.tagName)) {
    return node.tagName.name.text;
  }

  return node.tagName.getText();
}

function getAttributeName(attribute) {
  return ts.isJsxAttribute(attribute) ? attribute.name.text : '';
}

function getAttributeValue(attribute) {
  if (!ts.isJsxAttribute(attribute)) {
    return null;
  }

  if (!attribute.initializer) {
    return '';
  }

  if (ts.isStringLiteral(attribute.initializer)) {
    return attribute.initializer.text.trim();
  }

  if (ts.isJsxExpression(attribute.initializer)) {
    const expression = attribute.initializer.expression;
    if (!expression) {
      return '';
    }

    if (expression.kind === ts.SyntaxKind.TrueKeyword) {
      return 'true';
    }

    if (expression.kind === ts.SyntaxKind.FalseKeyword) {
      return 'false';
    }

    return getNodeTextContent(expression).trim() || null;
  }

  return null;
}

function hasMeaningfulExpressionText(node) {
  if (!node) {
    return false;
  }

  if (
    ts.isStringLiteralLike(node) ||
    ts.isNoSubstitutionTemplateLiteral(node) ||
    ts.isIdentifier(node) ||
    ts.isPropertyAccessExpression(node) ||
    ts.isElementAccessExpression(node) ||
    ts.isNumericLiteral(node)
  ) {
    return node.getText().replace(/['"`]/g, '').trim().length > 0;
  }

  if (ts.isTemplateExpression(node)) {
    return (
      node.head.text.trim().length > 0 ||
      node.templateSpans.some(
        (span) =>
          hasMeaningfulExpressionText(span.expression) ||
          span.literal.text.trim().length > 0,
      )
    );
  }

  if (ts.isParenthesizedExpression(node)) {
    return hasMeaningfulExpressionText(node.expression);
  }

  if (ts.isConditionalExpression(node)) {
    return (
      hasMeaningfulExpressionText(node.whenTrue) ||
      hasMeaningfulExpressionText(node.whenFalse)
    );
  }

  if (ts.isBinaryExpression(node)) {
    return (
      hasMeaningfulExpressionText(node.left) ||
      hasMeaningfulExpressionText(node.right)
    );
  }

  if (ts.isArrayLiteralExpression(node)) {
    return node.elements.some(hasMeaningfulExpressionText);
  }

  if (ts.isCallExpression(node)) {
    return true;
  }

  if (ts.isJsxElement(node) || ts.isJsxFragment(node)) {
    return getNodeTextContent(node).trim().length > 0;
  }

  return false;
}

function getNodeTextContent(node) {
  if (!node) {
    return '';
  }

  if (ts.isJsxText(node)) {
    return node.getText().replace(/\s+/g, ' ').trim();
  }

  if (ts.isJsxExpression(node)) {
    return hasMeaningfulExpressionText(node.expression) ? node.getText() : '';
  }

  if (ts.isJsxElement(node) || ts.isJsxFragment(node)) {
    return node.children.map(getNodeTextContent).join(' ').trim();
  }

  if (
    ts.isStringLiteralLike(node) ||
    ts.isNoSubstitutionTemplateLiteral(node) ||
    ts.isIdentifier(node) ||
    ts.isPropertyAccessExpression(node) ||
    ts.isElementAccessExpression(node) ||
    ts.isNumericLiteral(node)
  ) {
    return node.getText().replace(/['"`]/g, '').trim();
  }

  if (ts.isTemplateExpression(node)) {
    const segments = [node.head.text, ...node.templateSpans.flatMap((span) => [getNodeTextContent(span.expression), span.literal.text])];
    return segments.join(' ').trim();
  }

  if (ts.isConditionalExpression(node)) {
    return `${getNodeTextContent(node.whenTrue)} ${getNodeTextContent(node.whenFalse)}`.trim();
  }

  if (ts.isBinaryExpression(node)) {
    return `${getNodeTextContent(node.left)} ${getNodeTextContent(node.right)}`.trim();
  }

  return '';
}

function getJsxAttributes(node) {
  if (ts.isJsxSelfClosingElement(node)) {
    return node.attributes.properties;
  }

  if (ts.isJsxElement(node)) {
    return node.openingElement.attributes.properties;
  }

  return [];
}

function hasAccessibleName(node) {
  return getJsxAttributes(node).some((attribute) => {
    const name = getAttributeName(attribute);
    if (name !== 'aria-label' && name !== 'aria-labelledby') {
      return false;
    }

    const value = getAttributeValue(attribute);
    return value === null || value.length > 0;
  });
}

function hasVisibleOrReadableText(node) {
  if (ts.isJsxSelfClosingElement(node)) {
    return false;
  }

  return node.children.some(
    (child) => getNodeTextContent(child).trim().length > 0,
  );
}

function hasSpreadAttributes(node) {
  return getJsxAttributes(node).some((attribute) => ts.isJsxSpreadAttribute(attribute));
}

function isHiddenFromAssistiveTech(node) {
  return getJsxAttributes(node).some((attribute) => {
    if (getAttributeName(attribute) === 'hidden') {
      return true;
    }

    return getAttributeName(attribute) === 'aria-hidden' && getAttributeValue(attribute) === 'true';
  });
}

function isInteractiveControl(node) {
  const tagName = getJsxTagName(node);
  if (['button', 'Button', 'a', 'Link'].includes(tagName)) {
    return true;
  }

  return getJsxAttributes(node).some((attribute) => {
    if (getAttributeName(attribute) !== 'role') {
      return false;
    }

    const value = getAttributeValue(attribute);
    if (!value) {
      return false;
    }

    return value
      .split(/\s+/)
      .some((role) => ['button', 'link', 'menuitem', 'tab'].includes(role));
  });
}

function collectButtonAuditFindings() {
  const files = walkFiles(path.join(root, 'src'), (filePath) =>
    filePath.endsWith('.tsx'),
  );
  const findings = [];

  for (const filePath of files) {
    const source = fs.readFileSync(filePath, 'utf8');
    const sourceFile = ts.createSourceFile(
      filePath,
      source,
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TSX,
    );

    function visit(node) {
      if (ts.isJsxElement(node) || ts.isJsxSelfClosingElement(node)) {
        const tagName = getJsxTagName(node);
        if (
          isInteractiveControl(node) &&
          !isHiddenFromAssistiveTech(node) &&
          !hasAccessibleName(node) &&
          !hasSpreadAttributes(node) &&
          !hasVisibleOrReadableText(node)
        ) {
          const line = sourceFile.getLineAndCharacterOfPosition(node.getStart()).line + 1;
          findings.push(
            `${path.relative(root, filePath).replace(/\\/g, '/')}:${line} <${tagName}> is icon-only or empty without an accessible name`,
          );
        }
      }

      ts.forEachChild(node, visit);
    }

    visit(sourceFile);
  }

  return findings;
}

function collectImplementedAppRoutes() {
  const appRoot = path.join(root, 'src', 'app');
  return walkFiles(appRoot, (filePath) => path.basename(filePath) === 'page.tsx')
    .map((filePath) => {
      const routePath = path
        .relative(appRoot, path.dirname(filePath))
        .replace(/\\/g, '/');

      const normalizedRoute = routePath === '' ? '/' : `/${routePath}`;
      return (
        normalizedRoute.replace(/^\/(?:\[locale\]|en|vi)(?=\/|$)/, '') ||
        '/'
      );
    })
    .filter((route, index, routes) => routes.indexOf(route) === index)
    .sort();
}

function collectCoveredE2ERoutes() {
  const coveredRoutes = new Set([
    '/admin',
    '/dashboard/thesis',
    '/dashboard/thesis/topics/[id]',
    '/dashboard/lecturer/grades/[id]',
    '/dashboard/sign-out',
  ]);

  const e2eFiles = walkFiles(path.join(root, 'e2e'), (filePath) =>
    filePath.endsWith('.ts'),
  );

  for (const filePath of e2eFiles) {
    const source = fs.readFileSync(filePath, 'utf8');
    const publicRoutesMatch = source.match(
      /const publicRoutes = \[([\s\S]*?)\];/,
    );
    if (publicRoutesMatch) {
      for (const match of publicRoutesMatch[1].matchAll(/'([^']+)'/g)) {
        coveredRoutes.add(match[1]);
      }
    }

    for (const match of source.matchAll(/path:\s*'([^']+)'/g)) {
      coveredRoutes.add(match[1]);
    }
  }

  return [
    ...new Set(
      [...coveredRoutes].map(
        (route) => route.replace(/^\/(?:en|vi)(?=\/|$)/, '') || '/',
      ),
    ),
  ].sort();
}

function unwrapObjectLikeExpression(node) {
  if (!node) {
    return null;
  }

  if (ts.isObjectLiteralExpression(node)) {
    return node;
  }

  if (
    ts.isAsExpression(node) ||
    ts.isTypeAssertionExpression(node) ||
    ts.isSatisfiesExpression(node)
  ) {
    return unwrapObjectLikeExpression(node.expression);
  }

  if (ts.isParenthesizedExpression(node)) {
    return unwrapObjectLikeExpression(node.expression);
  }

  return null;
}

function findExportedObjectLiteral(sourceFile, exportName) {
  for (const statement of sourceFile.statements) {
    if (!ts.isVariableStatement(statement)) {
      continue;
    }

    for (const declaration of statement.declarationList.declarations) {
      const objectLiteral = unwrapObjectLikeExpression(declaration.initializer);
      if (
        ts.isIdentifier(declaration.name) &&
        declaration.name.text === exportName &&
        objectLiteral
      ) {
        return objectLiteral;
      }
    }
  }

  throw new Error(`Could not find exported object literal "${exportName}"`);
}

function collectObjectShape(node, prefix = '', acc = []) {
  if (ts.isObjectLiteralExpression(node)) {
    for (const property of node.properties) {
      if (!ts.isPropertyAssignment(property) && !ts.isShorthandPropertyAssignment(property)) {
        continue;
      }

      const keyNode = ts.isPropertyAssignment(property) ? property.name : property.name;
      const key =
        ts.isIdentifier(keyNode) || ts.isStringLiteralLike(keyNode) || ts.isNumericLiteral(keyNode)
          ? keyNode.text
          : keyNode.getText().replace(/['"`]/g, '');
      const pathKey = prefix ? `${prefix}.${key}` : key;
      acc.push(pathKey);

      const value = ts.isPropertyAssignment(property)
        ? property.initializer
        : property.name;
      if (ts.isObjectLiteralExpression(value) || ts.isArrayLiteralExpression(value)) {
        collectObjectShape(value, pathKey, acc);
      }
    }
  }

  if (ts.isArrayLiteralExpression(node)) {
    acc.push(`${prefix}[]:${node.elements.length}`);
    node.elements.forEach((element, index) => {
      if (ts.isObjectLiteralExpression(element) || ts.isArrayLiteralExpression(element)) {
        collectObjectShape(element, `${prefix}[${index}]`, acc);
      }
    });
  }

  return acc;
}

test('ThemeProvider always renders through the context provider', () => {
  const source = read('src/components/ThemeProvider.tsx');

  assert.match(source, /ThemeContext\.Provider/);
  assert.doesNotMatch(
    source,
    /if\s*\(!mounted\)\s*\{\s*return children;\s*\}/s,
  );
});

test('dashboard chrome exposes the implemented notifications center', () => {
  const source = read('src/app/dashboard/layout.tsx');

  assert.doesNotMatch(source, /\/admin\/students/);
  assert.match(source, /\/dashboard\/notifications/);
  assert.ok(
    fs.existsSync(path.join(root, 'src', 'app', 'dashboard', 'notifications', 'page.tsx')),
  );
  assert.doesNotMatch(source, /\/dashboard\/settings/);
  assert.match(
    source,
    /aria-label=\{messages\.dashboardShell\.controls\.toggleNotifications\}/,
  );
  assert.match(
    source,
    /aria-label=\{messages\.dashboardShell\.controls\.toggleProfile\}/,
  );
});

test('admin hub only links to implemented routes', () => {
  const source = read('src/app/admin/page.tsx');

  assert.doesNotMatch(source, /\/admin\/students/);
});

test('admin users exposes a mobile card list alongside the desktop table', () => {
  const source = read('src/app/admin/users/page.tsx');

  assert.match(source, /role="list"/);
  assert.match(source, /className="space-y-3 md:hidden"/);
  assert.match(source, /<AdminTableScroll className="hidden md:block">/);
});

test('admin course and classroom views expose mobile card lists', () => {
  for (const relPath of ['src/app/admin/courses/page.tsx', 'src/app/admin/classrooms/page.tsx']) {
    const source = read(relPath);
    assert.match(source, /role="list"/);
    assert.match(source, /className="space-y-3 md:hidden"/);
    assert.match(source, /<AdminTableScroll className="hidden md:block">/);
  }
});

test('admin department, semester, and lecturer views expose mobile card lists', () => {
  for (const relPath of [
    'src/app/admin/departments/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/lecturers/page.tsx',
  ]) {
    const source = read(relPath);
    assert.match(source, /role="list"/);
    assert.match(source, /className="space-y-3 md:hidden"/);
    assert.match(source, /<AdminTableScroll className="hidden md:block">/);
  }
});

test('admin invoice, enrollment, and section views expose mobile card lists', () => {
  for (const relPath of [
    'src/app/admin/invoices/page.tsx',
    'src/app/admin/enrollments/page.tsx',
    'src/app/admin/sections/page.tsx',
  ]) {
    const source = read(relPath);
    assert.match(source, /role="list"/);
    assert.match(source, /className="space-y-3 md:hidden"/);
    assert.match(source, /<AdminTableScroll className="hidden md:block">/);
  }
});

test('admin academic-year and analytics views expose mobile card lists', () => {
  const academicYearSource = read('src/app/admin/academic-years/page.tsx');
  assert.match(academicYearSource, /role="list"/);
  assert.match(academicYearSource, /className="space-y-3 md:hidden"/);
  assert.match(academicYearSource, /<AdminTableScroll className="hidden md:block">/);

  const analyticsSource = read('src/app/admin/analytics/page.tsx');
  assert.match(analyticsSource, /role="list"/);
  assert.match(analyticsSource, /className="mt-5 space-y-3 md:hidden"/);
  assert.match(analyticsSource, /<AdminTableScroll className="mt-5 hidden md:block">/);
});

test('workspace grade and invoice views expose mobile card lists', () => {
  for (const relPath of [
    'src/app/dashboard/grades/page.tsx',
    'src/app/dashboard/invoices/page.tsx',
    'src/app/dashboard/transcript/page.tsx',
    'src/app/dashboard/lecturer/grades/[id]/page.tsx',
  ]) {
    const source = read(relPath);
    assert.match(source, /role="list"/);
    assert.match(source, /className="space-y-3 md:hidden"/);
    assert.match(source, /className="hidden overflow-x-auto md:block"/);
  }
});

test('homepage copy avoids demo placeholders and dead hrefs', () => {
  const source = read('src/app/page.tsx');
  const deadHrefPattern = new RegExp('href="' + '#"');
  const contactSalesPattern = new RegExp(['Contact', 'Sales'].join(' '));

  assert.doesNotMatch(source, deadHrefPattern);
  assert.doesNotMatch(source, /50K\+/);
  assert.match(source, /messages\.home\.metricCards\.map/);
  assert.match(source, /messages\.home\.whyTitle/);
  assert.doesNotMatch(source, contactSalesPattern);
  assert.match(source, /href=\{user \? '\/dashboard' : '\/login'\}/);
});

test('auth client uses cookie sessions and CSRF headers', () => {
  const apiSource = read('src/lib/api.ts');
  const authContextSource = read('src/context/AuthContext.tsx');

  assert.match(apiSource, /NEXT_PUBLIC_API_URL \|\| '\/api\/v1'/);
  assert.match(apiSource, /withCredentials:\s*true/);
  assert.match(apiSource, /cc_csrf/);
  assert.match(apiSource, /X-CSRF-Token/);
  assert.match(
    apiSource,
    /await api\.post\(\s*'\/auth\/logout',\s*\{\}\s*,\s*\{[\s\S]*skipAuthRefresh:\s*true[\s\S]*skipAuthRedirect:\s*true[\s\S]*\}\s+as AuthRequestConfig,\s*\);/s,
  );
  assert.match(apiSource, /redirectToLogin\('session-expired'\)/);
  assert.match(apiSource, /redirectToLogin\('unauthorized'\)/);
  assert.match(apiSource, /skipAuthRefresh:\s*true/);
  assert.doesNotMatch(apiSource, /localStorage\.(getItem|setItem|removeItem)/);
  assert.doesNotMatch(
    authContextSource,
    /localStorage\.(getItem|setItem|removeItem)/,
  );
  assert.match(
    authContextSource,
    /router\.replace\(`\$\{href\('\/login'\)\}\?reason=signed-out`\)/,
  );
});

test('high-frequency auth and profile forms associate labels with controls', () => {
  const associations = [
    ['src/app/forgot-password/page.tsx', 'forgot-password-email'],
    ['src/app/reset-password/page.tsx', 'reset-password-new'],
    ['src/app/reset-password/page.tsx', 'reset-password-confirm'],
    ['src/app/dashboard/profile/page.tsx', 'profile-first-name'],
    ['src/app/dashboard/profile/page.tsx', 'profile-last-name'],
    ['src/app/dashboard/profile/page.tsx', 'profile-email'],
    ['src/app/dashboard/profile/page.tsx', 'profile-phone'],
    ['src/app/dashboard/profile/page.tsx', 'profile-date-of-birth'],
    ['src/app/dashboard/profile/page.tsx', 'profile-address'],
    ['src/app/dashboard/profile/page.tsx', 'profile-current-password'],
    ['src/app/dashboard/profile/page.tsx', 'profile-new-password'],
    ['src/app/dashboard/profile/page.tsx', 'profile-confirm-password'],
  ];

  for (const [relPath, controlId] of associations) {
    const source = read(relPath);
    assert.match(source, new RegExp(`htmlFor="${controlId}"`));
    assert.match(source, new RegExp(`id="${controlId}"`));
  }
});

test('frontend config exposes local edge rewrites and SEO runtime files', () => {
  const nextConfigSource = fs.readFileSync(
    path.join(root, 'next.config.mjs'),
    'utf8',
  );
  const envExampleSource = read('.env.example');
  const layoutSource = read('src/app/layout.tsx');
  const serverMetadataSource = read('src/i18n/server.ts');
  const proxyHelperSource = read('src/lib/local-edge-proxy.ts');

  assert.match(nextConfigSource, /source:\s*'\/api\/v1\/:path\*'/);
  assert.match(nextConfigSource, /LOCAL_EDGE_ORIGIN/);
  assert.match(nextConfigSource, /ENABLE_LOCAL_EDGE_REWRITES/);
  assert.match(envExampleSource, /NEXT_PUBLIC_SITE_URL=https:\/\/tienson\.io\.vn/);
  assert.match(envExampleSource, /NEXT_PUBLIC_API_URL=\/api\/v1/);
  assert.match(envExampleSource, /LOCAL_EDGE_ORIGIN=http:\/\/127\.0\.0\.1:8080/);
  assert.match(envExampleSource, /ENABLE_LOCAL_EDGE_REWRITES=0/);
  assert.match(layoutSource, /<html lang=\{htmlLang\}/);
  assert.match(layoutSource, /themeColor:/);
  assert.match(serverMetadataSource, /metadataBase:\s*new URL\(getSiteUrl\(\)\)/);
  assert.match(serverMetadataSource, /alternates:/);
  assert.doesNotMatch(serverMetadataSource, /themeColor:/);
  assert.match(serverMetadataSource, /manifest:\s*'\/manifest\.webmanifest'/);
  assert.match(proxyHelperSource, /proxyToLocalEdge/);
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'api', 'v1', '[...path]', 'route.ts')));
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'api', 'docs', '[[...path]]', 'route.ts')));
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'health', 'route.ts')));
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'robots.ts')));
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'sitemap.ts')));
  assert.ok(fs.existsSync(path.join(root, 'src', 'app', 'manifest.ts')));
});

test('locale middleware, dictionaries, and shell toggles stay aligned', () => {
  const middlewareSource = readFirstExisting([
    'src/proxy.ts',
    'proxy.ts',
    'src/middleware.ts',
    'middleware.ts',
  ]);
  const messagesSource = read('src/i18n/messages.ts');
  const sourceFile = ts.createSourceFile(
    'messages.ts',
    messagesSource,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  );
  const englishShape = collectObjectShape(findExportedObjectLiteral(sourceFile, 'en')).sort();
  const vietnameseShape = collectObjectShape(findExportedObjectLiteral(sourceFile, 'vi')).sort();

  assert.match(middlewareSource, /cc_locale/);
  assert.match(middlewareSource, /stripLocaleFromPathname/);
  assert.match(middlewareSource, /x-cc-locale/);
  assert.deepEqual(vietnameseShape, englishShape);

  for (const relPath of [
    'src/app/page.tsx',
    'src/components/auth/AuthShell.tsx',
    'src/components/admin/AdminFrame.tsx',
    'src/app/dashboard/layout.tsx',
  ]) {
    assert.match(read(relPath), /LanguageToggle/);
  }
});

test('shared UI no longer contains obvious mojibake arrows', () => {
  const source = read('src/components/ui/data-table.tsx');

  assert.doesNotMatch(source, /â†‘|â†“/);
});

test('root layout uses a stable internal font stack for Vietnamese UI copy', () => {
  const layoutSource = read('src/app/layout.tsx');
  const globalsSource = read('src/app/globals.css');

  assert.doesNotMatch(layoutSource, /next\/font\/google/);
  assert.match(globalsSource, /--font-sans:\s*"Be Vietnam Pro",\s*system-ui,\s*"Segoe UI",\s*Roboto,\s*"Helvetica Neue",\s*Arial,\s*sans-serif;/);
  assert.match(globalsSource, /font-family:\s*var\(--font-sans\),/);
  assert.match(globalsSource, /button,\s*[\r\n]+\s*input,\s*[\r\n]+\s*select,\s*[\r\n]+\s*textarea\s*\{/);
});

test('all button-like controls expose text or an accessible name across frontend routes', () => {
  const findings = collectButtonAuditFindings();

  assert.deepEqual(
    findings,
    [],
    `Found icon-only controls without accessible names:\n${findings.map((finding) => `- ${finding}`).join('\n')}`,
  );
});

test('every implemented app route is covered by fast E2E smoke', () => {
  const implementedRoutes = collectImplementedAppRoutes();
  const coveredRoutes = collectCoveredE2ERoutes();

  assert.deepEqual(
    implementedRoutes,
    coveredRoutes,
    [
      'Fast E2E route coverage is out of sync with implemented app routes.',
      `Implemented: ${implementedRoutes.join(', ')}`,
      `Covered: ${coveredRoutes.join(', ')}`,
    ].join('\n'),
  );
});

test('modal exposes dialog semantics and a labeled close control', () => {
  const source = read('src/components/ui/modal.tsx');

  assert.match(source, /role="dialog"/);
  assert.match(source, /aria-modal="true"/);
  assert.match(source, /closeLabel\?: string/);
  assert.match(source, /aria-label=\{closeLabel \|\| messages\.common\.states\.closeModal\}/);
});

test('frontend routes do not use raw browser confirm dialogs', () => {
  const routeFiles = walkFiles(path.join(root, 'src', 'app'), (filePath) =>
    filePath.endsWith('.tsx'),
  );

  const offenders = routeFiles
    .filter((filePath) => {
      const source = fs.readFileSync(filePath, 'utf8');
      return /window\.confirm\s*\(/.test(source);
    })
    .map((filePath) => path.relative(root, filePath).replace(/\\/g, '/'));

  assert.deepEqual(offenders, []);
});

test('legacy admin CRUD surfaces use shared admin surface primitives', () => {
  const polishedAdminRoutes = [
    'src/app/admin/academic-years/page.tsx',
    'src/app/admin/announcements/page.tsx',
    'src/app/admin/classrooms/page.tsx',
    'src/app/admin/courses/page.tsx',
    'src/app/admin/departments/page.tsx',
    'src/app/admin/enrollments/page.tsx',
    'src/app/admin/invoices/page.tsx',
    'src/app/admin/lecturers/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/users/page.tsx',
  ];

  for (const routeFile of polishedAdminRoutes) {
    const source = read(routeFile);
    assert.match(source, /AdminToolbarCard/);
    assert.match(source, /AdminTableCard/);
    assert.match(source, /AdminRowActions/);
  }
});

test('admin overview and analytics reuse the shared admin metric and panel grammar', () => {
  const adminOverviewSource = read('src/app/admin/page.tsx');
  const adminAnalyticsSource = read('src/app/admin/analytics/page.tsx');

  assert.match(adminOverviewSource, /AdminMetricCard/);
  assert.match(adminOverviewSource, /AdminTableCard/);
  assert.match(adminAnalyticsSource, /AdminMetricCard/);
  assert.match(adminAnalyticsSource, /AdminTableCard/);
});

test('student and lecturer workspace surfaces use shared dashboard primitives', () => {
  const studentOverviewSource = read('src/app/dashboard/page.tsx');
  const profileSource = read('src/app/dashboard/profile/page.tsx');
  const lecturerOverviewSource = read('src/app/dashboard/lecturer/page.tsx');
  const lecturerGradesSource = read('src/app/dashboard/lecturer/grades/page.tsx');

  assert.match(studentOverviewSource, /WorkspaceMetricCard/);
  assert.match(studentOverviewSource, /WorkspacePanel/);
  assert.match(studentOverviewSource, /WorkspaceActionTile/);
  assert.match(profileSource, /WorkspacePanel/);
  assert.match(lecturerOverviewSource, /WorkspaceMetricCard/);
  assert.match(lecturerOverviewSource, /WorkspacePanel/);
  assert.match(lecturerOverviewSource, /WorkspaceActionTile/);
  assert.match(lecturerGradesSource, /WorkspaceMetricCard/);
  assert.match(lecturerGradesSource, /WorkspacePanel/);
});

test('Stitch workspace guardrails keep long values and assistant clear of mobile navigation', () => {
  const metricSource = read('src/components/dashboard/WorkspaceSurface.tsx');
  const studentDashboardSource = read('src/app/dashboard/page.tsx');
  const assistantSource = read('src/components/assistant/AssistantPanel.tsx');
  const shellSource = read('src/app/dashboard/layout.tsx');
  const globalsSource = read('src/app/globals.css');

  assert.match(metricSource, /break-normal/);
  assert.match(studentDashboardSource, /valueClassName="text-xl sm:text-2xl"/);
  assert.match(assistantSource, /bottom-\[5\.75rem\].*z-50/);
  assert.match(shellSource, /backdrop-blur md:hidden/);
  assert.match(globalsSource, /--background: 240 100% 98\.8%/);
});

test('key frontend surfaces label icon-only buttons', () => {
  assertPatterns('src/app/login/page.tsx', [
    /aria-label=\{showPassword \? messages\.login\.hidePassword : messages\.login\.showPassword\}/,
  ]);
  assertPatterns('src/app/reset-password/page.tsx', [
    /aria-label=\{showPassword \? messages\.login\.hidePassword : messages\.login\.showPassword\}/,
  ]);
  assertPatterns('src/app/dashboard/invoices/page.tsx', [
    /closeLabel=\{copy\.closeDetail\}/,
    /handleContinueCheckout/,
    /getCheckoutActionLabel/,
  ]);
  assertPatterns('src/app/admin/academic-years/page.tsx', [
    /aria-label=\{copy\.editLabel\(record\.year\)\}/,
    /aria-label=\{copy\.deleteLabel\(record\.year\)\}/,
  ]);
  assertPatterns('src/app/admin/announcements/page.tsx', [
    /aria-label=\{copy\.deleteLabel\(announcement\.title\)\}/,
    /closeLabel=\{copy\.closeModal\}/,
  ]);
  assertPatterns('src/app/admin/classrooms/page.tsx', [
    /aria-label=\{copy\.editLabel\(room\.building, room\.roomNumber\)\}/,
    /aria-label=\{copy\.deleteLabel\(room\.building, room\.roomNumber\)\}/,
  ]);
  assertPatterns('src/app/admin/courses/page.tsx', [
    /aria-label=\{copy\.editLabel\(courseLabel\)\}/,
    /aria-label=\{copy\.deleteLabel\(courseLabel\)\}/,
  ]);
  assertPatterns('src/app/admin/departments/page.tsx', [
    /aria-label=\{copy\.editLabel\(departmentLabel\)\}/,
    /aria-label=\{copy\.deleteLabel\(departmentLabel\)\}/,
  ]);
  assertPatterns('src/app/admin/enrollments/page.tsx', [
    /aria-label=\{copy\.viewLabel\(learnerLabel\)\}/,
    /aria-label=\{copy\.deleteLabel\(learnerLabel\)\}/,
    /closeLabel=\{copy\.closeDetail\}/,
  ]);
  assertPatterns('src/app/admin/invoices/page.tsx', [
    /aria-label=\{copy\.viewLabel\(invoice\.invoiceNumber\)\}/,
    /aria-label=\{copy\.deleteLabel\(invoice\.invoiceNumber\)\}/,
    /closeLabel=\{copy\.closeDetail\}/,
  ]);
  assertPatterns('src/app/admin/lecturers/page.tsx', [
    /aria-label=\{copy\.editLabel\(lecturer\.employeeId\)\}/,
    /aria-label=\{copy\.deleteLabel\(lecturer\.employeeId\)\}/,
  ]);
  assertPatterns('src/app/admin/sections/page.tsx', [
    /aria-label=\{copy\.editLabel\(/,
    /aria-label=\{copy\.deleteLabel\(/,
    /aria-label=\{copy\.removeSchedule\(idx \+ 1\)\}/,
  ]);
  assertPatterns('src/app/admin/semesters/page.tsx', [
    /aria-label=\{copy\.editLabel\(semesterLabel\)\}/,
    /aria-label=\{copy\.deleteLabel\(semesterLabel\)\}/,
  ]);
  assertPatterns('src/app/admin/users/page.tsx', [
    /aria-label=\{copy\.editUserLabel\(/,
    /aria-label=\{copy\.deleteUserLabel\(/,
  ]);
  assertPatterns('src/app/admin/page.tsx', [
    /href="\/admin\/users"/,
    /href="\/admin\/analytics"/,
  ]);
  assertPatterns('src/components/ui/data-table.tsx', [
    /aria-label=\{messages\.common\.states\.goToPreviousPage\}/,
    /aria-label=\{messages\.common\.states\.goToNextPage\}/,
  ]);
  assertPatterns('src/app/dashboard/lecturer/announcements/page.tsx', [
    /aria-label=\{copy\.backToDashboard\}/,
  ]);
  assertPatterns('src/app/dashboard/lecturer/grades/\[id\]/page.tsx', [
    /aria-label=\{copy\.backToGrades\}/,
  ]);
});

test('student invoice checkout uses provider handoff instead of inline sandbox status controls', () => {
  const source = read('src/app/dashboard/invoices/page.tsx');
  const adminInvoiceSource = read('src/app/admin/invoices/page.tsx');
  const financeContentSource = read('src/lib/finance-content.ts');

  assert.match(source, /handleContinueCheckout/);
  assert.match(source, /nextAction\?\.flow/);
  assert.doesNotMatch(source, /handleSandboxSignal/);
  assert.match(source, /getLocalizedInvoiceItemDescription/);
  assert.match(adminInvoiceSource, /getLocalizedInvoiceItemDescription/);
  assert.match(financeContentSource, /Học phí và dịch vụ campus/);
});

test('admin CRUD routes rely on localized AdminFrame back labels', () => {
  for (const relPath of [
    'src/app/admin/academic-years/page.tsx',
    'src/app/admin/announcements/page.tsx',
    'src/app/admin/classrooms/page.tsx',
    'src/app/admin/courses/page.tsx',
    'src/app/admin/departments/page.tsx',
    'src/app/admin/enrollments/page.tsx',
    'src/app/admin/invoices/page.tsx',
    'src/app/admin/lecturers/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/semesters/page.tsx',
  ]) {
    assert.doesNotMatch(read(relPath), /backLabel=/);
  }
});

test('localization smoke keeps bilingual coverage anchored to localized routes', () => {
  const source = read('e2e/localization.spec.ts');

  assert.match(source, /page\.goto\('\/vi'\)/);
  assert.match(source, /page\.goto\('\/vi\/login'\)/);
  assert.match(source, /page\.goto\('\/vi\/dashboard'\)/);
  assert.match(source, /toHaveURL\(\/\\\/vi\\\/admin/);
  assert.match(source, /getByRole\('button', \{ name: \/.*English.*\/i \}\)/);
});
