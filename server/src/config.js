require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });

const JWT_SECRET = process.env.JWT_SECRET || 'dev-change-me-in-production';
const JWT_ACCESS_TTL_SEC = Number(process.env.JWT_ACCESS_TTL_SEC || 1800);
const JWT_REFRESH_TTL_SEC = Number(process.env.JWT_REFRESH_TTL_SEC || 60 * 60 * 24 * 30);
const BCRYPT_ROUNDS = Number(process.env.BCRYPT_ROUNDS || 12);
const MIN_RUN_MS_PER_ITEM = Number(process.env.MIN_RUN_MS_PER_ITEM || 300);

const CHALLENGES = [
  {
    code: 'multiplication',
    title: 'Таблица умножения',
    variants: [10, 20, 30],
    description: 'Примеры с ответами от 10 до 99',
  },
  {
    code: 'spelling_ru',
    title: 'Словарные слова',
    variants: [10, 20, 30],
    description: 'Картинка из букваря — напиши слово',
  },
];

const ALLOWED_CHALLENGES = new Set(CHALLENGES.map(c => c.code));
const ALLOWED_VARIANTS = new Set([10, 20, 30]);
const ALLOWED_PERIODS = new Set(['day', 'week', 'season', 'all']);

module.exports = {
  JWT_SECRET,
  JWT_ACCESS_TTL_SEC,
  JWT_REFRESH_TTL_SEC,
  BCRYPT_ROUNDS,
  MIN_RUN_MS_PER_ITEM,
  CHALLENGES,
  ALLOWED_CHALLENGES,
  ALLOWED_VARIANTS,
  ALLOWED_PERIODS,
};
