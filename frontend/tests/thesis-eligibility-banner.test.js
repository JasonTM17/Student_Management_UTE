const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function source(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

// The eligibility read model already arrives caller-scoped on the round
// (student callers, covered round types only). The detail page must surface
// the same banner the workspace list shows — no extra endpoint.
test('a shared ThesisEligibilityBanner component renders the compact status', () => {
  const banner = source('src/components/thesis/ThesisEligibilityBanner.tsx');
  assert.match(banner, /eligibilityTitle/, 'banner keeps the eligibility title');
  assert.match(banner, /eligibilityEligible/, 'banner keeps the eligible copy');
  assert.match(banner, /eligibilityIneligible/, 'banner keeps the ineligible copy');
  assert.match(banner, /eligibilityCredits/, 'banner keeps the credits line');
  assert.match(banner, /eligibilityCpa/, 'banner keeps the CPA line');
});

test('round detail page shows the banner to students when the read model carries eligibility', () => {
  const detail = source('src/app/dashboard/thesis/[roundId]/page.tsx');
  assert.match(detail, /import \{ ThesisEligibilityBanner \}/, 'detail page must import the shared banner');
  assert.match(detail, /roles\?\.includes\('STUDENT'\)[\s\S]{0,120}ThesisEligibilityBanner/, 'banner must stay student-scoped');
  assert.match(detail, /round\.eligibility/, 'banner must key off the caller-scoped round eligibility');
});

test('workspace list reuses the shared banner instead of a divergent copy', () => {
  const list = source('src/app/dashboard/thesis/page.tsx');
  assert.match(list, /<ThesisEligibilityBanner eligibility=\{selectedRound\.eligibility\}/, 'list page must render the shared banner');
  assert.doesNotMatch(list, /eligibilityCpaMissing/, 'list page must not inline its own eligibility markup');
});
