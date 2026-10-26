const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const apiRoot = path.resolve(root, '..', 'java-services', 'restful-api');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const readApi = (relative) => fs.readFileSync(path.join(apiRoot, relative), 'utf8');

const COUNCIL_SERVICE = 'src/main/java/io/campuscore/restfulapi/thesis/service/ThesisCouncilService.java';
const TOPIC_SERVICE = 'src/main/java/io/campuscore/restfulapi/thesis/service/ThesisTopicService.java';
const PUBLICATION_SERVICE = 'src/main/java/io/campuscore/restfulapi/thesis/service/ThesisPublicationService.java';
const PUBLICATION_REPO = 'src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisPublicationRepository.java';
const SCOPE_REPO = 'src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisScopeRepository.java';
const MUTATION_DTOS = 'src/main/java/io/campuscore/restfulapi/thesis/dto/ThesisMutationDtos.java';

test('assigned reviewer identity escapes dean faculty scope on the score read', () => {
  const service = readApi(COUNCIL_SERVICE);
  // The read path must resolve the assigned reviewer before the faculty
  // scope check, and skip the scope check for that reviewer only.
  assert.match(service, /boolean assignedReviewer = lecturerId != null && lecturerId\.equals\(assigned\)/);
  assert.match(service, /if \(!governance && !assignedReviewer\)[\s\S]{0,200}REVIEWER_REQUIRED/);
  assert.match(service, /if \(!assignedReviewer\) \{\s*ThesisDeanScope\.requireTopicAccess/);
});

test('reviewer assignment refuses a profile that can never authenticate', () => {
  const service = readApi(COUNCIL_SERVICE);
  assert.match(service, /LECTURER_ACCOUNT_REQUIRED/);
  assert.match(service, /lecturerAccountActive\(lecturerId\)/);
  assert.match(service, /JOIN campuscore_auth\.\\"User\\" u ON u\.\\"id\\" = l\.\\"userId\\" AND u\.\\"status\\" = 'ACTIVE'/);
});

test('finalize refusals carry reviewer identity and score counts', () => {
  const service = readApi(COUNCIL_SERVICE);
  assert.match(service, /SCORES_INCOMPLETE[\s\S]{0,400}Map\.of\("submitted", submittedScores, "expected", expectedScores\)/);
  assert.match(service, /findLecturerDisplayNames[\s\S]{0,400}REVIEW_SCORE_REQUIRED[\s\S]{0,400}Map\.of\("reviewerId", gvpbLecturerId, "reviewer", reviewerName\)/);
  assert.match(service, /REVIEW_SCORE_BELOW_THRESHOLD[\s\S]{0,400}Map\.of\("score", gvpbScores\.get\(0\)\)/);
});

test('the dean topic catalog includes assigned counter-review topics', () => {
  const service = readApi(TOPIC_SERVICE);
  const scope = readApi(SCOPE_REPO);
  assert.match(service, /findReviewAssignedTopicIds/);
  assert.match(service, /reviewedByDean\.contains\(topic\.getId\(\)\)/);
  assert.match(scope, /gvpb_lecturer_id = :lecturerId/);
});

test('publication preview and refusal name the ungraded topics', () => {
  const service = readApi(PUBLICATION_SERVICE);
  const repo = readApi(PUBLICATION_REPO);
  const dtos = readApi(MUTATION_DTOS);
  assert.match(dtos, /record PublishResultsUngradedTopic\(UUID id, String title\)/);
  assert.match(dtos, /List<PublishResultsUngradedTopic> ungradedTopics/);
  assert.match(service, /listUngradedTopics/);
  assert.match(service, /listUngradedIdTitles/);
  assert.match(repo, /List<PublishResultsUngradedTopic> listUngradedTopics/);
  // The queries join the approved group to a topic with no final score.
  assert.match(repo, /final_score IS NULL/);
  assert.match(repo, /approval_status = 'APPROVED'/);
});

test('publish preview type carries optional ungraded topics for older payloads', () => {
  const api = read('src/lib/thesis-api.ts');
  const lifecycle = read('src/lib/thesis-round-lifecycle.ts');
  assert.match(api, /interface ThesisUngradedTopic/);
  assert.match(api, /ungradedTopics: ThesisUngradedTopic\[\]/);
  assert.match(lifecycle, /ungradedTopics\?: \{ id: string; title: string \}\[\]/);
  assert.match(lifecycle, /export function publishBlockerTopicTitles/);
  assert.match(lifecycle, /REVIEW_WINDOW_OPEN[\s\S]{0,300}openReviewTopics/);
});

test('the admin publish blocker names the topics that gate it', () => {
  const page = read('src/app/admin/thesis/page.tsx');
  assert.match(page, /publishBlockerMessage\(resultPreview, blockerCode\)/);
  assert.match(page, /publishBlockerMessage\(publishReadiness\.preview, publishResultsBlocker\)/);
  assert.match(page, /publishBlockerTopicTitles\(preview, code\)/);
  assert.match(page, /publishBlockerTopics\.replace\('\{titles\}'/);
});

test('reviewer picker drops profiles without a live account', () => {
  const rules = read('src/lib/thesis-picker-rules.ts');
  assert.match(rules, /conflictNoUserAccount/);
  assert.match(rules, /user\?: \{ status\?: string \| null \} \| null/);
  assert.match(rules, /!args\.lecturer\.userId[\s\S]{0,300}conflictNoUserAccount/);
});

test('dashboard thesis errors interpolate structured details', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  assert.match(page, /campusCodeMessage\(caught, messages\.common\.campusErrors, messages\.thesis\.actionFailed\)/);
  assert.match(page, /import \{ campusCodeMessage \} from '@\/lib\/campus-error'/);
});

test('localized copy covers the new reviewer and publication blockers', () => {
  const messages = read('src/i18n/messages.ts');
  assert.match(messages, /LECTURER_ACCOUNT_REQUIRED:[\s\S]{0,200}active user account/);
  assert.match(messages, /LECTURER_ACCOUNT_REQUIRED:[\s\S]{0,200}tài khoản đang hoạt động/);
  assert.match(messages, /conflictNoUserAccount: 'No active user account/);
  assert.match(messages, /conflictNoUserAccount: 'Không có tài khoản hoạt động/);
  assert.match(messages, /publishBlockerTopics: 'Blocking topic\(s\): \{titles\}\.'/);
  assert.match(messages, /publishBlockerTopics: 'Đề tài đang chặn: \{titles\}\.'/);
  // The reviewer placeholder interpolates from error.details.reviewer.
  assert.match(messages, /REVIEW_SCORE_REQUIRED:[\s\S]{0,300}\{reviewer\}/);
  assert.match(messages, /REVIEW_SCORE_BELOW_THRESHOLD:[\s\S]{0,300}\{score\}/);
});
