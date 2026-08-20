import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const routes = fs.readFileSync(path.join(mobileRoot, 'src/navigation/routes.ts'), 'utf8');
const tokens = fs.readFileSync(path.join(mobileRoot, 'src/design/tokens.ts'), 'utf8');
const readme = fs.readFileSync(path.join(mobileRoot, 'README.md'), 'utf8');
const navigator = fs.readFileSync(path.join(mobileRoot, 'src/navigation/MobileNavigator.tsx'), 'utf8');
const bottomNavigation = fs.readFileSync(path.join(mobileRoot, 'src/components/BottomNavigation.tsx'), 'utf8');
const signIn = fs.readFileSync(path.join(mobileRoot, 'src/screens/auth/SignInScreen.tsx'), 'utf8');
const navigationTypes = fs.readFileSync(path.join(mobileRoot, 'src/navigation/types.ts'), 'utf8');
const screenComponents = fs.readFileSync(path.join(mobileRoot, 'src/screens/index.ts'), 'utf8');
const stitchMetadata = JSON.parse(
  fs.readFileSync(path.join(mobileRoot, '..', 'frontend', '.stitch', 'metadata.json'), 'utf8'),
);

test('native registry keeps the Stitch mobile atlas above the 20-screen requirement', () => {
  const registeredRoutes = [...routes.matchAll(/\{ name: '([^']+)'/g)].map((match) => match[1]);
  const componentRoutes = [
    ...screenComponents.matchAll(/^\s*(?:'([^']+)'|([A-Za-z][\w.]*))\s*:/gm),
  ].map((match) => match[1] ?? match[2]);
  const screenCount = registeredRoutes.length;

  assert.equal(screenCount, 23);
  assert.equal(new Set(registeredRoutes).size, screenCount);
  assert.deepEqual(componentRoutes.sort(), [...registeredRoutes].sort());
  for (const requiredRoute of [
    'auth.signIn',
    'dashboard.student',
    'thesis.topics',
    'thesis.evaluation',
    'assistant.chat',
    'admin.dashboard',
    'lecturer.grading',
  ]) {
    assert.match(routes, new RegExp(`name: '${requiredRoute.replace('.', '\\.')}'`));
  }
  assert.match(readme, /23 navigable screens/);
});

test('native tokens preserve the Academic Continuity contract', () => {
  assert.match(tokens, /background: '#F9F9FF'/);
  assert.match(tokens, /primary: '#003F87'/);
  assert.match(tokens, /primaryContainer: '#0056B3'/);
  assert.match(tokens, /mobileGutter: 16/);
  assert.match(tokens, /touchTarget: 44/);
  assert.match(tokens, /family: 'Be Vietnam Pro'/);
});

test('native API seam fails closed until live mode is explicitly enabled', () => {
  const client = fs.readFileSync(path.join(mobileRoot, 'src/api/client.ts'), 'utf8');

  assert.match(client, /EXPO_PUBLIC_API_MODE === 'live'/);
  assert.match(client, /MOBILE_API_PREVIEW/);
  assert.match(client, /mode: ApiMode/);
  assert.match(client, /export type AssistantLocale = 'en' \| 'vi'/);
  assert.match(client, /assistantChat: \(message: string, locale: AssistantLocale = 'en'\)/);
  assert.match(client, /apiClient\.post<AssistantReply>\(apiRoutes\.thesis\.assistantChat, \{ message, locale \}\)/);
});

test('mobile role navigation rejects unauthorized routes and uses role-specific primary navigation', () => {
  assert.match(routes, /export function canAccessScreen\(role: UserRole, route: ScreenName\)/);
  assert.match(routes, /const bottomNavigationByRole: Readonly<Record<UserRole, readonly BottomNavigationItem\[\]>>/);
  assert.match(routes, /lecturer:\s*\[[\s\S]*?route: 'lecturer\.grading'/);
  assert.match(routes, /admin:\s*\[[\s\S]*?route: 'admin\.students'/);
  assert.match(navigator, /if \(!canAccessScreen\(role, nextRoute\)\) \{[\s\S]*?setRoute\(roleHome\[role\]\)/);
  assert.match(navigator, /<BottomNavigation[\s\S]*?role=\{role\}/);
  assert.match(bottomNavigation, /getBottomNavigation\(role\)/);
  assert.match(bottomNavigation, /role: UserRole/);
  assert.match(navigator, /if \(!hasActiveSession\) \{\s*return;/);
  assert.match(navigator, /if \(!isPreviewSession\) \{\s*return;/);
  assert.match(navigator, /type SessionKind = 'signedOut' \| 'preview' \| 'authenticated'/);
  assert.match(navigator, /const isAuthenticated = sessionKind === 'authenticated'/);
  assert.match(navigator, /if \(route !== 'auth\.signIn' \|\| apiClient\.mode !== 'preview'\)/);
  assert.match(navigationTypes, /enterPreview\(\): void/);
});

test('Stitch mobile references stay traceable and preview sign-in does not impersonate live authentication', () => {
  const stitchReferenceIds = (routes.match(/id: '([0-9a-f]{32})'/g) ?? [])
    .map((match) => match.slice(5, -1));
  const metadataIds = new Set(
    Object.values(stitchMetadata.screens).map((screen) => screen.id),
  );

  assert.equal(stitchReferenceIds.length, 13);
  assert.equal(stitchMetadata.inventory.screenCount, 22);
  assert.equal(stitchMetadata.inventory.mobileCount, 13);
  assert.equal(Object.keys(stitchMetadata.screens).length, 22);
  assert.equal(
    Object.values(stitchMetadata.screens).filter((screen) => screen.deviceType === 'MOBILE').length,
    13,
  );
  for (const referenceId of stitchReferenceIds) {
    assert.ok(metadataIds.has(referenceId), `Missing Stitch metadata for ${referenceId}`);
  }
  assert.match(routes, /export const stitchMobileReferences/);
  assert.match(routes, /'thesis\.evaluation': \[[\s\S]*?3073bce589eb4d4e97ef9775e921a506/);
  assert.match(routes, /'admin\.lecturers': \[[\s\S]*?36c60dec6ed9458a80bc5b1cfe6f82a5/);
  assert.match(signIn, /const isPreview = apiClient\.mode === 'preview'/);
  assert.match(signIn, /label=\{isPreview \? 'Explore preview' : 'Live sign in unavailable'\}/);
  assert.match(signIn, /navigation\.enterPreview\(\)/);
  assert.doesNotMatch(signIn, /navigation\.navigate\('dashboard\.student'\)/);
  assert.match(signIn, /No account is authenticated until the Java auth contract is implemented and verified/);
  assert.match(readme, /role-specific bottom bar/);
});
