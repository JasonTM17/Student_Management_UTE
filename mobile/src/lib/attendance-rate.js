/** @typedef {{ present: number, late: number, absent: number }} AttendanceCounts */

/**
 * Calculates the attended percentage. Excused records are omitted from the
 * countable-session inputs; positive half values round up (HALF_UP).
 *
 * @param {AttendanceCounts} counts
 * @param {number} [fractionDigits=0]
 * @returns {number | null}
 */
function calculateAttendanceRate({ present, late, absent }, fractionDigits = 0) {
  const countable = present + late + absent;
  if (!Number.isFinite(countable) || countable <= 0) return null;

  const attended = present + late;
  const scale = 10 ** fractionDigits;
  return Math.round((attended * 100 * scale) / countable) / scale;
}

module.exports = { calculateAttendanceRate };
