const { db } = require('./db');
const { ALLOWED_CHALLENGES, ALLOWED_VARIANTS, MIN_RUN_MS_PER_ITEM } = require('./config');
const { generateNonce } = require('./auth-utils');

function startRun(userId, challengeCode, variant) {
  if (!ALLOWED_CHALLENGES.has(challengeCode)) {
    return { ok: false, error: 'Unknown challenge' };
  }
  if (!ALLOWED_VARIANTS.has(variant)) {
    return { ok: false, error: 'Invalid variant' };
  }

  const now = Date.now();
  const nonce = generateNonce();
  const result = db.prepare(
    `INSERT INTO runs (user_id, challenge_code, variant, status, server_nonce, started_at)
     VALUES (?, ?, ?, 'started', ?, ?)`
  ).run(userId, challengeCode, variant, nonce, now);

  return {
    ok: true,
    runId: Number(result.lastInsertRowid),
    serverNonce: nonce,
    startedAt: now,
  };
}

function finishRun(userId, payload) {
  const runId = Number(payload.runId);
  const mainTime = Number(payload.mainTime);
  const totalTime = Number(payload.totalTime);
  const errors = Number(payload.errors);
  const attempts = Number(payload.attempts);

  const run = db.prepare('SELECT * FROM runs WHERE id = ? AND user_id = ?').get(runId, userId);
  if (!run) {
    return { ok: false, error: 'Run not found' };
  }
  if (run.status !== 'started') {
    return { ok: false, error: 'Run already finished' };
  }

  if (!Number.isInteger(mainTime) || mainTime < 0) {
    return { ok: false, error: 'Invalid mainTime' };
  }
  if (!Number.isInteger(totalTime) || totalTime < 0) {
    return { ok: false, error: 'Invalid totalTime' };
  }
  if (!Number.isInteger(errors) || errors < 0) {
    return { ok: false, error: 'Invalid errors' };
  }
  if (!Number.isInteger(attempts) || attempts < run.variant) {
    return { ok: false, error: 'Invalid attempts' };
  }

  const minPossible = run.variant * MIN_RUN_MS_PER_ITEM;
  if (totalTime < minPossible) {
    return { ok: false, error: 'Suspiciously fast result' };
  }

  const finishedAt = Date.now();
  db.prepare(
    `UPDATE runs
     SET status = 'finished', main_time = ?, total_time = ?, errors = ?, attempts = ?, finished_at = ?
     WHERE id = ?`
  ).run(mainTime, totalTime, errors, attempts, finishedAt, runId);

  return {
    ok: true,
    run: {
      id: runId,
      challengeCode: run.challenge_code,
      variant: run.variant,
      mainTime,
      totalTime,
      errors,
      attempts,
      finishedAt,
    },
  };
}

module.exports = { startRun, finishRun };
