const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const RELEASES_DIR = process.env.RELEASES_DIR || '/var/www/multiplication-trainer/releases';
const MANIFEST_PATH = path.join(RELEASES_DIR, 'latest.json');

function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function getLatestRelease(baseUrl) {
  if (!fs.existsSync(MANIFEST_PATH)) {
    return { available: false };
  }

  try {
    const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'));
    const apkPath = path.join(RELEASES_DIR, manifest.fileName || '');
    if (!manifest.fileName || !fs.existsSync(apkPath)) {
      return { available: false };
    }

    const sha256 = manifest.sha256 || sha256File(apkPath);
    return {
      available: true,
      versionCode: manifest.versionCode,
      versionName: manifest.versionName,
      apkUrl: `${baseUrl}/releases/${manifest.fileName}`,
      sha256,
      size: fs.statSync(apkPath).size,
      changelog: manifest.changelog || '',
    };
  } catch (error) {
    return { available: false };
  }
}

module.exports = { getLatestRelease, RELEASES_DIR, MANIFEST_PATH };
