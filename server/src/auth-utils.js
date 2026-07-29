const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { JWT_SECRET, JWT_ACCESS_TTL_SEC } = require('./config');

function signAccessToken(user) {
  return jwt.sign(
    { sub: user.id, login: user.login },
    JWT_SECRET,
    { expiresIn: JWT_ACCESS_TTL_SEC }
  );
}

function verifyAccessToken(token) {
  return jwt.verify(token, JWT_SECRET);
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function generateRefreshToken() {
  return crypto.randomBytes(48).toString('base64url');
}

function generateNonce() {
  return crypto.randomBytes(16).toString('hex');
}

function authMiddleware(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  try {
    const payload = verifyAccessToken(token);
    req.user = { id: Number(payload.sub), login: payload.login };
    return next();
  } catch (error) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

module.exports = {
  signAccessToken,
  verifyAccessToken,
  hashToken,
  generateRefreshToken,
  generateNonce,
  authMiddleware,
};
