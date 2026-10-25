import test from 'node:test';
import assert from 'node:assert/strict';
import attendanceRate from '../src/lib/attendance-rate.js';

test('mobile attendance aggregate follows the shared attended-session definition', () => {
  assert.equal(attendanceRate.calculateAttendanceRate({ present: 1, late: 1, absent: 1, excused: 1 }), 67);
  assert.equal(attendanceRate.calculateAttendanceRate({ present: 1, late: 0, absent: 39 }), 3);
  assert.equal(attendanceRate.calculateAttendanceRate({ present: 0, late: 0, absent: 0 }), null);
});
