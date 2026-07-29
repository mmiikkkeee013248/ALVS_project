const bcrypt = require('bcryptjs');
const { db } = require('./db');
const {
  BCRYPT_ROUNDS,
  JWT_REFRESH_TTL_SEC,
} = require('./config');
const {
  signAccessToken,
  hashToken,
  generateRefreshToken,
} = require('./auth-utils');

const LOGIN_RE = /^[a-zA-Z0-9_.-]{3,20}$/;
const DISPLAY_NAME_RE = /^[\p{L}\p{N} _.\-]{2,20}$/u;

function normalizeLogin(value) {
  return String(value || '').trim().toLowerCase();
}

function normalizeDisplayName(value) {
  return String(value || '').trim().replace(/\s+/g, ' ');
}

function registerUser({ login, password, displayName }) {
  const normalizedLogin = normalizeLogin(login);
  const normalizedDisplayName = normalizeDisplayName(displayName);

  if (!LOGIN_RE.test(normalizedLogin)) {
    return { ok: false, error: 'Login must be 3-20 latin letters, digits, _, . or -' };
  }
  if (!password || password.length < 6) {
    return { ok: false, error: 'Password must be at least 6 characters' };
  }
  if (!DISPLAY_NAME_RE.test(normalizedDisplayName)) {
    return { ok: false, error: 'Display name must be 2-20 characters' };
  }

  const existing = db.prepare('SELECT id FROM users WHERE login = ?').get(normalizedLogin);
  if (existing) {
    return { ok: false, error: 'Login already taken' };
  }

  const now = Date.now();
  const passwordHash = bcrypt.hashSync(password, BCRYPT_ROUNDS);
  const insertUser = db.prepare(
    'INSERT INTO users (login, password_hash, created_at) VALUES (?, ?, ?)'
  );
  const result = insertUser.run(normalizedLogin, passwordHash, now);
  const userId = Number(result.lastInsertRowid);

  db.prepare(
    'INSERT INTO profiles (user_id, display_name, cosmetics_json) VALUES (?, ?, ?)'
  ).run(userId, normalizedDisplayName, JSON.stringify({ avatarId: null, tableThemeId: null, badges: [] }));

  return { ok: true, userId };
}

function loginUser({ login, password, deviceId }) {
  const normalizedLogin = normalizeLogin(login);
  const user = db.prepare('SELECT * FROM users WHERE login = ?').get(normalizedLogin);
  if (!user || user.status !== 'active') {
    return { ok: false, error: 'Invalid login or password' };
  }

  const valid = bcrypt.compareSync(password, user.password_hash);
  if (!valid) {
    return { ok: false, error: 'Invalid login or password' };
  }

  return createSession(user, deviceId);
}

function createSession(user, deviceId) {
  const accessToken = signAccessToken(user);
  const refreshToken = generateRefreshToken();
  const now = Date.now();
  const expiresAt = now + JWT_REFRESH_TTL_SEC * 1000;

  db.prepare(
    `INSERT INTO refresh_tokens (user_id, token_hash, device_id, expires_at, created_at)
     VALUES (?, ?, ?, ?, ?)`
  ).run(user.id, hashToken(refreshToken), deviceId || null, expiresAt, now);

  const profile = db.prepare('SELECT display_name, cosmetics_json FROM profiles WHERE user_id = ?').get(user.id);

  return {
    ok: true,
    accessToken,
    refreshToken,
    expiresIn: require('./config').JWT_ACCESS_TTL_SEC,
    user: {
      id: user.id,
      login: user.login,
      displayName: profile.display_name,
      cosmetics: JSON.parse(profile.cosmetics_json || '{}'),
    },
  };
}

function refreshSession(refreshToken) {
  if (!refreshToken) {
    return { ok: false, error: 'Refresh token required' };
  }

  const tokenHash = hashToken(refreshToken);
  const row = db.prepare(
    `SELECT rt.id AS token_id, rt.user_id, rt.expires_at, u.login, u.status
     FROM refresh_tokens rt
     JOIN users u ON u.id = rt.user_id
     WHERE rt.token_hash = ?`
  ).get(tokenHash);

  if (!row || row.status !== 'active') {
    return { ok: false, error: 'Invalid refresh token' };
  }
  if (row.expires_at < Date.now()) {
    db.prepare('DELETE FROM refresh_tokens WHERE id = ?').run(row.token_id);
    return { ok: false, error: 'Refresh token expired' };
  }

  db.prepare('DELETE FROM refresh_tokens WHERE id = ?').run(row.token_id);
  const user = { id: row.user_id, login: row.login };
  return createSession(user, null);
}

function logoutUser(refreshToken) {
  if (!refreshToken) return;
  db.prepare('DELETE FROM refresh_tokens WHERE token_hash = ?').run(hashToken(refreshToken));
}

function getProfile(userId) {
  const row = db.prepare(
    `SELECT u.id, u.login, p.display_name, p.cosmetics_json
     FROM users u
     JOIN profiles p ON p.user_id = u.id
     WHERE u.id = ?`
  ).get(userId);

  if (!row) return null;

  return {
    id: row.id,
    login: row.login,
    displayName: row.display_name,
    cosmetics: JSON.parse(row.cosmetics_json || '{}'),
  };
}

function updateProfile(userId, displayName) {
  const normalizedDisplayName = normalizeDisplayName(displayName);
  if (!DISPLAY_NAME_RE.test(normalizedDisplayName)) {
    return { ok: false, error: 'Display name must be 2-20 characters' };
  }

  db.prepare('UPDATE profiles SET display_name = ? WHERE user_id = ?').run(normalizedDisplayName, userId);
  return { ok: true, profile: getProfile(userId) };
}

module.exports = {
  registerUser,
  loginUser,
  refreshSession,
  logoutUser,
  getProfile,
  updateProfile,
};
