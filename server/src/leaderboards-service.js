const { db } = require('./db');
const { ALLOWED_CHALLENGES, ALLOWED_VARIANTS, ALLOWED_PERIODS } = require('./config');

const MSK_OFFSET_MS = 3 * 60 * 60 * 1000;

function getMskYMD(now = Date.now()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(now));
  const get = type => Number(parts.find(part => part.type === type).value);
  return { year: get('year'), month: get('month'), day: get('day') };
}

function mskMidnightUtcMs(year, month, day) {
  return Date.UTC(year, month - 1, day) - MSK_OFFSET_MS;
}

function getPeriodStart(period, now = Date.now()) {
  if (period === 'all') return 0;

  const { year, month, day } = getMskYMD(now);

  if (period === 'day') {
    return mskMidnightUtcMs(year, month, day);
  }

  if (period === 'week') {
    const date = new Date(mskMidnightUtcMs(year, month, day));
    const dayOfWeek = date.getUTCDay();
    const mondayOffset = (dayOfWeek + 6) % 7;
    date.setUTCDate(date.getUTCDate() - mondayOffset);
    return date.getTime();
  }

  if (period === 'season') {
    const seasonMonth = Math.floor((month - 1) / 3) * 3 + 1;
    return mskMidnightUtcMs(year, seasonMonth, 1);
  }

  return 0;
}

function getLeaderboard(challengeCode, variant, period) {
  if (!ALLOWED_CHALLENGES.has(challengeCode)) {
    return { ok: false, error: 'Unknown challenge' };
  }
  if (!ALLOWED_VARIANTS.has(variant)) {
    return { ok: false, error: 'Invalid variant' };
  }
  if (!ALLOWED_PERIODS.has(period)) {
    return { ok: false, error: 'Invalid period' };
  }

  const periodStart = getPeriodStart(period);
  const rows = db.prepare(
    `WITH ranked AS (
       SELECT
         r.user_id AS userId,
         p.display_name AS displayName,
         r.total_time AS totalTime,
         r.main_time AS mainTime,
         r.errors AS errors,
         r.finished_at AS finishedAt,
         ROW_NUMBER() OVER (
           PARTITION BY r.user_id
           ORDER BY r.total_time ASC, r.errors ASC, r.main_time ASC, r.finished_at ASC
         ) AS rn
       FROM runs r
       JOIN profiles p ON p.user_id = r.user_id
       WHERE r.challenge_code = ?
         AND r.variant = ?
         AND r.status = 'finished'
         AND r.finished_at >= ?
     )
     SELECT userId, displayName, totalTime, mainTime, errors, finishedAt
     FROM ranked
     WHERE rn = 1
     ORDER BY totalTime ASC, errors ASC, mainTime ASC, finishedAt ASC
     LIMIT 100`
  ).all(challengeCode, variant, periodStart);

  const entries = rows.map((row, index) => ({
    rank: index + 1,
    userId: row.userId,
    displayName: row.displayName,
    totalTime: row.totalTime,
    mainTime: row.mainTime,
    errors: row.errors,
    finishedAt: row.finishedAt,
  }));

  return { ok: true, entries, periodStart };
}

function getPersonalBest(userId, challengeCode, variant, period) {
  const board = getLeaderboard(challengeCode, variant, period);
  if (!board.ok) return board;
  const entry = board.entries.find(item => item.userId === userId) || null;
  return { ok: true, entry };
}

module.exports = { getLeaderboard, getPersonalBest, getPeriodStart };
