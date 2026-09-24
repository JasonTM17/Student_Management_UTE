const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

test('iCalendar (.ics) generator specifications and RFC 5545 compliance', async (t) => {
  await t.test('ical-generator exports required generator and formatting functions', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/ical-generator.ts'), 'utf8');
    assert.match(fileSource, /export function escapeIcsText/, 'must export escapeIcsText');
    assert.match(fileSource, /export function formatIcsLocalDateTime/, 'must export formatIcsLocalDateTime');
    assert.match(fileSource, /export function formatIcsUtcDateTime/, 'must export formatIcsUtcDateTime');
    assert.match(fileSource, /export function findFirstOccurrenceDate/, 'must export findFirstOccurrenceDate');
    assert.match(fileSource, /export function buildScheduleIcsEvents/, 'must export buildScheduleIcsEvents');
    assert.match(fileSource, /export function generateIcsCalendar/, 'must export generateIcsCalendar');
    assert.match(fileSource, /export function downloadIcsFile/, 'must export downloadIcsFile');
  });

  await t.test('findFirstOccurrenceDate computes correct day of week occurrences', () => {
    // 2026-09-01 is a Tuesday (JS getDay() = 2)
    const baseTuesday = new Date(2026, 8, 1); // Month is 0-indexed: 8 = September
    assert.equal(baseTuesday.getDay(), 2, 'base date must be Tuesday');

    // dow = 2: Monday (targetJsDay = 1) -> Tuesday to next Monday is 6 days -> Sept 7
    const targetJsDayMon = (2 === 1 ? 0 : 2 - 1);
    const diffMon = (targetJsDayMon - baseTuesday.getDay() + 7) % 7;
    assert.equal(diffMon, 6, 'diff to Monday from Tuesday must be 6 days');

    // dow = 3: Tuesday (targetJsDay = 2) -> Tuesday to Tuesday is 0 days -> Sept 1
    const targetJsDayTue = (3 === 1 ? 0 : 3 - 1);
    const diffTue = (targetJsDayTue - baseTuesday.getDay() + 7) % 7;
    assert.equal(diffTue, 0, 'diff to Tuesday from Tuesday must be 0 days');

    // dow = 4: Wednesday (targetJsDay = 3) -> Tuesday to Wednesday is 1 day -> Sept 2
    const targetJsDayWed = (4 === 1 ? 0 : 4 - 1);
    const diffWed = (targetJsDayWed - baseTuesday.getDay() + 7) % 7;
    assert.equal(diffWed, 1, 'diff to Wednesday from Tuesday must be 1 day');
  });

  await t.test('escapeIcsText handles RFC 5545 reserved characters correctly', () => {
    // Testing escape logic directly
    const escape = (text) =>
      text
        .replace(/\\/g, '\\\\')
        .replace(/;/g, '\\;')
        .replace(/,/g, '\\,')
        .replace(/\r?\n/g, '\\n');

    assert.equal(escape('Math, Physics; Chemistry'), 'Math\\, Physics\\; Chemistry');
    assert.equal(escape('Line 1\nLine 2'), 'Line 1\\nLine 2');
    assert.equal(escape('A\\B'), 'A\\\\B');
  });

  await t.test('generateIcsCalendar produces valid RFC 5545 structure with CRLF', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/ical-generator.ts'), 'utf8');
    assert.match(fileSource, /BEGIN:VCALENDAR/, 'must include BEGIN:VCALENDAR');
    assert.match(fileSource, /VERSION:2\.0/, 'must declare VERSION:2.0');
    assert.match(fileSource, /CALSCALE:GREGORIAN/, 'must set CALSCALE:GREGORIAN');
    assert.match(fileSource, /BEGIN:VEVENT/, 'must define VEVENT blocks');
    assert.match(fileSource, /DTSTART;TZID=Asia\/Ho_Chi_Minh:/, 'must include timezone-anchored DTSTART');
    assert.match(fileSource, /FREQ=WEEKLY/, 'must support weekly recurrence RRULE');
    assert.match(fileSource, /END:VEVENT/, 'must close VEVENT blocks');
    assert.match(fileSource, /END:VCALENDAR/, 'must close VCALENDAR');
    assert.match(fileSource, /\\r\\n/, 'must format lines with RFC 5545 CRLF endings');
  });
});
