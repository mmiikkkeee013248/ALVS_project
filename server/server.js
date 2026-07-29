require('dotenv').config();

const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const { CHALLENGES } = require('./src/config');
const { authMiddleware } = require('./src/auth-utils');
const {
  registerUser,
  loginUser,
  refreshSession,
  logoutUser,
  getProfile,
  updateProfile,
} = require('./src/auth-service');
const { startRun, finishRun } = require('./src/runs-service');
const { getLeaderboard, getPersonalBest } = require('./src/leaderboards-service');
const { getLatestRelease } = require('./src/releases-service');

const app = express();
const PORT = Number(process.env.PORT || 8091);

app.set('trust proxy', 1);
app.use(cors());
app.use(express.json({ limit: '32kb' }));

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many auth attempts, try later' },
});

const runLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests' },
});

app.get('/api/health', (_req, res) => {
  res.json({ ok: true });
});

app.post('/api/auth/register', authLimiter, (req, res) => {
  const result = registerUser(req.body || {});
  if (!result.ok) return res.status(400).json({ error: result.error });
  const session = loginUser({
    login: req.body.login,
    password: req.body.password,
    deviceId: req.body.deviceId,
  });
  return res.status(201).json(session);
});

app.post('/api/auth/login', authLimiter, (req, res) => {
  const result = loginUser(req.body || {});
  if (!result.ok) return res.status(401).json({ error: result.error });
  return res.json(result);
});

app.post('/api/auth/refresh', authLimiter, (req, res) => {
  const result = refreshSession(req.body?.refreshToken);
  if (!result.ok) return res.status(401).json({ error: result.error });
  return res.json(result);
});

app.post('/api/auth/logout', (req, res) => {
  logoutUser(req.body?.refreshToken);
  return res.json({ ok: true });
});

app.get('/api/me', authMiddleware, (req, res) => {
  const profile = getProfile(req.user.id);
  if (!profile) return res.status(404).json({ error: 'User not found' });
  return res.json(profile);
});

app.patch('/api/me/profile', authMiddleware, (req, res) => {
  const result = updateProfile(req.user.id, req.body?.displayName);
  if (!result.ok) return res.status(400).json({ error: result.error });
  return res.json(result.profile);
});

app.get('/api/challenges', (_req, res) => {
  res.json({ challenges: CHALLENGES });
});

app.post('/api/runs/start', authMiddleware, runLimiter, (req, res) => {
  const { challengeCode, variant } = req.body || {};
  const result = startRun(req.user.id, challengeCode, Number(variant));
  if (!result.ok) return res.status(400).json({ error: result.error });
  return res.status(201).json(result);
});

app.post('/api/runs/finish', authMiddleware, runLimiter, (req, res) => {
  const result = finishRun(req.user.id, req.body || {});
  if (!result.ok) return res.status(400).json({ error: result.error });
  return res.json(result);
});

app.get('/api/leaderboards/:challenge/:variant/:period', (req, res) => {
  const variant = Number(req.params.variant);
  const result = getLeaderboard(req.params.challenge, variant, req.params.period);
  if (!result.ok) return res.status(400).json({ error: result.error });
  return res.json({ entries: result.entries, periodStart: result.periodStart });
});

app.get('/api/leaderboards/:challenge/:variant/:period/me', authMiddleware, (req, res) => {
  const variant = Number(req.params.variant);
  const result = getPersonalBest(
    req.user.id,
    req.params.challenge,
    variant,
    req.params.period
  );
  if (!result.ok) return res.status(400).json({ error: result.error });
  return res.json({ entry: result.entry });
});

app.get('/api/releases/latest', (req, res) => {
  const proto = req.headers['x-forwarded-proto'] || req.protocol;
  const host = req.headers['x-forwarded-host'] || req.headers.host;
  const baseUrl = `${proto}://${host}`;
  return res.json(getLatestRelease(baseUrl));
});

// Legacy endpoints for old web client (optional backward compat)
app.get('/api/leaderboard/:difficulty', (req, res) => {
  const variant = Number(req.params.difficulty);
  const result = getLeaderboard('multiplication', variant, 'all');
  if (!result.ok) return res.status(400).json({ error: result.error });
  const entries = result.entries.map(entry => ({
    nickname: entry.displayName,
    totalTime: entry.totalTime,
    mainTime: entry.mainTime,
    errors: entry.errors,
    attempts: 0,
    timestamp: entry.finishedAt,
    rank: entry.rank,
  }));
  return res.json({ entries });
});

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
});

app.listen(PORT, '127.0.0.1', () => {
  console.log(`Platform API listening on 127.0.0.1:${PORT}`);
});
