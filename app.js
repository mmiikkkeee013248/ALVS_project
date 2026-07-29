(function () {
  'use strict';

  const API_BASE_URL = getApiBaseUrl();
  const NICKNAME_STORAGE_KEY = 'mt_player_nickname';
  const MAX_NICKNAME_LENGTH = 20;

  const CanonicalPool = [];
  for (let left = 2; left <= 9; left++) {
    for (let right = left; right <= 9; right++) {
      if (left * right >= 10 && left * right <= 99) {
        CanonicalPool.push({ left, right, answer: left * right });
      }
    }
  }

  const state = {
    screen: 'HOME',
    selectedDifficulty: 10,
    leaderboardTab: 10,
    playerNickname: loadSavedNickname(),
    problems: [],
    mainIndex: 0,
    phase: 'MAIN',
    retryQueue: [],
    currentProblem: null,
    typedAnswer: '',
    inputEnabled: false,
    accumulatedTimeMs: 0,
    questionStartTime: 0,
    mainTimeMs: 0,
    errors: 0,
    attempts: 0,
    timerInterval: null,
    finalResult: null,
    leaderboardRank: null,
    personalBestRequestId: 0,
    leaderboardRequestId: 0,
  };

  const screens = {
    HOME: document.getElementById('screen-home'),
    GAME: document.getElementById('screen-game'),
    RESULT: document.getElementById('screen-result'),
    LEADERBOARD: document.getElementById('screen-leaderboard'),
  };

  const modeSelector = document.getElementById('mode-selector');
  const btnStartGame = document.getElementById('btn-start-game');
  const btnOpenLb = document.getElementById('btn-open-leaderboard');
  const btnHomeLogo = document.getElementById('btn-home-logo');
  const nicknameInput = document.getElementById('player-nickname');
  const nicknameHint = document.getElementById('nickname-hint');
  const personalBestValue = document.getElementById('personal-best-value');

  const gameCard = document.getElementById('game-card');
  const phasePill = document.getElementById('game-phase-pill');
  const phaseText = document.getElementById('game-phase-text');
  const liveTimer = document.getElementById('game-live-timer');
  const progressFill = document.getElementById('game-progress-fill');
  const progressCount = document.getElementById('game-progress-count');
  const errorBadge = document.getElementById('game-error-badge');
  const problemText = document.getElementById('problem-text');
  const digit1 = document.getElementById('digit-1');
  const digit2 = document.getElementById('digit-2');
  const keypad = document.getElementById('keypad');

  const resTotalTime = document.getElementById('res-total-time');
  const resMainTime = document.getElementById('res-main-time');
  const resErrors = document.getElementById('res-errors');
  const resAttempts = document.getElementById('res-attempts');
  const rankBanner = document.getElementById('result-rank-banner');
  const rankPosText = document.getElementById('result-rank-pos');
  const resultSyncStatus = document.getElementById('result-sync-status');
  const btnResHome = document.getElementById('btn-res-home');
  const btnResRestart = document.getElementById('btn-res-restart');

  const btnCloseLb = document.getElementById('btn-close-leaderboard');
  const lbTabGroup = document.getElementById('lb-tab-group');
  const lbTableBody = document.getElementById('lb-table-body');
  const lbEmpty = document.getElementById('lb-empty');

  nicknameInput.value = state.playerNickname;
  refreshNicknameState();
  refreshPersonalBest();

  modeSelector.addEventListener('click', e => {
    const btn = e.target.closest('.mode-btn');
    if (!btn) return;
    document.querySelectorAll('.mode-btn').forEach(item => item.classList.remove('active'));
    btn.classList.add('active');
    state.selectedDifficulty = parseInt(btn.dataset.difficulty, 10);
    refreshPersonalBest();
  });

  nicknameInput.addEventListener('input', () => {
    const normalized = normalizeNickname(nicknameInput.value);
    state.playerNickname = normalized;
    localStorage.setItem(NICKNAME_STORAGE_KEY, normalized);
    refreshNicknameState();
    refreshPersonalBest();
  });

  btnStartGame.addEventListener('click', startGame);
  btnOpenLb.addEventListener('click', () => openLeaderboard(state.selectedDifficulty));
  btnHomeLogo.addEventListener('click', goHome);
  btnResHome.addEventListener('click', goHome);
  btnResRestart.addEventListener('click', startGame);

  keypad.addEventListener('click', e => {
    const btn = e.target.closest('.key-btn');
    if (!btn || btn.disabled || !state.inputEnabled) return;
    const key = btn.dataset.key;
    if (key !== undefined) {
      handleDigitInput(parseInt(key, 10));
    }
  });

  window.addEventListener('keydown', e => {
    if (state.screen !== 'GAME' || !state.inputEnabled) return;
    if (e.key >= '0' && e.key <= '9') {
      handleDigitInput(parseInt(e.key, 10));
    }
  });

  lbTabGroup.addEventListener('click', e => {
    const tab = e.target.closest('.lb-tab');
    if (!tab) return;
    state.leaderboardTab = parseInt(tab.dataset.difficulty, 10);
    updateLeaderboardTabs();
    renderLeaderboardTable();
  });

  btnCloseLb.addEventListener('click', () => {
    showScreen(state.finalResult ? 'RESULT' : 'HOME');
  });

  function showScreen(screenName) {
    state.screen = screenName;
    Object.keys(screens).forEach(key => {
      screens[key].classList.toggle('active', key === screenName);
    });
  }

  function goHome() {
    stopTimer();
    showScreen('HOME');
    refreshPersonalBest();
  }

  function startGame() {
    const nickname = normalizeNickname(nicknameInput.value);
    if (!nickname) {
      nicknameInput.classList.add('invalid');
      nicknameHint.textContent = 'Введите ник: только буквы, цифры, пробел, точка, дефис или _.';
      nicknameHint.classList.add('error');
      nicknameInput.focus();
      return;
    }

    state.playerNickname = nickname;
    nicknameInput.value = nickname;
    localStorage.setItem(NICKNAME_STORAGE_KEY, nickname);
    refreshNicknameState();

    state.problems = generateProblems(state.selectedDifficulty);
    state.mainIndex = 0;
    state.phase = 'MAIN';
    state.retryQueue = [];
    state.accumulatedTimeMs = 0;
    state.mainTimeMs = 0;
    state.errors = 0;
    state.attempts = 0;
    state.typedAnswer = '';
    state.inputEnabled = true;
    state.leaderboardRank = null;
    state.finalResult = null;

    showScreen('GAME');
    updatePhaseUI();
    loadProblem(state.problems[0]);
    startTimer();
  }

  function loadProblem(problem) {
    state.currentProblem = problem;
    state.typedAnswer = '';
    state.inputEnabled = true;
    state.questionStartTime = performance.now();

    problemText.textContent = `${problem.left} × ${problem.right}`;
    updateDigitDisplay();
    updateProgressUI();
  }

  function updateDigitDisplay() {
    digit1.textContent = state.typedAnswer.length >= 1 ? state.typedAnswer[0] : '_';
    digit2.textContent = state.typedAnswer.length >= 2 ? state.typedAnswer[1] : '_';
  }

  function updateProgressUI() {
    let currentNum;
    let totalNum;
    let percent;

    if (state.phase === 'MAIN') {
      currentNum = state.mainIndex + 1;
      totalNum = state.selectedDifficulty;
      percent = Math.round((state.mainIndex / state.selectedDifficulty) * 100);
    } else {
      currentNum = state.selectedDifficulty;
      totalNum = state.selectedDifficulty;
      percent = 100;
    }

    progressCount.textContent = `${currentNum} / ${totalNum}`;
    progressFill.style.width = `${percent}%`;
    errorBadge.textContent = `Ошибок: ${state.errors}`;
  }

  function updatePhaseUI() {
    if (state.phase === 'MAIN') {
      phasePill.classList.remove('retry');
      phaseText.textContent = 'Основной круг';
    } else {
      phasePill.classList.add('retry');
      phaseText.textContent = 'Работа над ошибками';
    }
  }

  function startTimer() {
    stopTimer();
    state.timerInterval = setInterval(() => {
      const now = performance.now();
      const currentQTime = state.inputEnabled && state.questionStartTime > 0 ? now - state.questionStartTime : 0;
      liveTimer.textContent = formatTime(state.accumulatedTimeMs + currentQTime);
    }, 50);
  }

  function stopTimer() {
    if (state.timerInterval) {
      clearInterval(state.timerInterval);
      state.timerInterval = null;
    }
  }

  function handleDigitInput(digit) {
    if (state.typedAnswer.length >= 2) return;

    state.typedAnswer += digit;
    updateDigitDisplay();

    if (state.typedAnswer.length === 2) {
      submitAnswer(parseInt(state.typedAnswer, 10));
    }
  }

  function submitAnswer(userAnswer) {
    state.inputEnabled = false;
    const now = performance.now();
    const responseTimeMs = now - state.questionStartTime;
    state.accumulatedTimeMs += responseTimeMs;
    state.attempts++;

    const isCorrect = userAnswer === state.currentProblem.answer;

    if (!isCorrect) {
      state.errors++;
      state.retryQueue.push(state.currentProblem);
      gameCard.classList.add('feedback-wrong');
    } else {
      gameCard.classList.add('feedback-correct');
    }

    setTimeout(() => {
      gameCard.classList.remove('feedback-correct', 'feedback-wrong');
      advance(isCorrect);
    }, isCorrect ? 200 : 400);
  }

  function advance(wasCorrect) {
    if (state.phase === 'MAIN') {
      state.mainIndex++;
      if (state.mainIndex < state.problems.length) {
        loadProblem(state.problems[state.mainIndex]);
      } else {
        state.mainTimeMs = state.accumulatedTimeMs;
        if (state.retryQueue.length === 0) {
          finishGame();
        } else {
          state.phase = 'RETRY';
          updatePhaseUI();
          loadProblem(state.retryQueue.shift());
        }
      }
      return;
    }

    if (state.retryQueue.length === 0) {
      finishGame();
    } else {
      loadProblem(state.retryQueue.shift());
    }
  }

  function finishGame() {
    stopTimer();

    const finalResult = {
      nickname: state.playerNickname,
      difficulty: state.selectedDifficulty,
      mainTime: Math.round(state.mainTimeMs > 0 ? state.mainTimeMs : state.accumulatedTimeMs),
      totalTime: Math.round(state.accumulatedTimeMs),
      errors: state.errors,
      attempts: state.attempts,
      timestamp: Date.now(),
    };

    state.finalResult = finalResult;
    state.leaderboardRank = null;

    resTotalTime.textContent = formatTime(finalResult.totalTime);
    resMainTime.textContent = formatTime(finalResult.mainTime);
    resErrors.textContent = finalResult.errors;
    resAttempts.textContent = finalResult.attempts;
    resultSyncStatus.textContent = 'Сохраняем результат на сервере...';
    resultSyncStatus.classList.remove('error');
    rankBanner.classList.add('hidden');

    showScreen('RESULT');
    saveResultToServer(finalResult);
  }

  async function saveResultToServer(finalResult) {
    const resultTimestamp = finalResult.timestamp;
    try {
      const response = await fetchJson(`${API_BASE_URL}/leaderboard`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(finalResult),
      });

      if (!state.finalResult || state.finalResult.timestamp !== resultTimestamp) {
        return;
      }

      state.leaderboardRank = response.rank;
      resultSyncStatus.textContent = 'Результат сохранён в общем рейтинге.';

      if (response.rank <= 100) {
        rankPosText.textContent = `#${response.rank}`;
        rankBanner.classList.remove('hidden');
      } else {
        rankBanner.classList.add('hidden');
      }

      refreshPersonalBest();
    } catch (error) {
      if (!state.finalResult || state.finalResult.timestamp !== resultTimestamp) {
        return;
      }

      resultSyncStatus.textContent = 'Не удалось сохранить результат. Проверьте подключение к серверу.';
      resultSyncStatus.classList.add('error');
      rankBanner.classList.add('hidden');
    }
  }

  function openLeaderboard(difficulty) {
    state.leaderboardTab = difficulty;
    updateLeaderboardTabs();
    showScreen('LEADERBOARD');
    renderLeaderboardTable();
  }

  function updateLeaderboardTabs() {
    document.querySelectorAll('.lb-tab').forEach(tab => {
      tab.classList.toggle('active', parseInt(tab.dataset.difficulty, 10) === state.leaderboardTab);
    });
  }

  async function renderLeaderboardTable() {
    const requestId = ++state.leaderboardRequestId;
    lbTableBody.innerHTML = '';
    lbEmpty.classList.remove('hidden');
    lbEmpty.textContent = 'Загрузка рейтинга...';

    try {
      const response = await fetchJson(`${API_BASE_URL}/leaderboard/${state.leaderboardTab}`);
      if (requestId !== state.leaderboardRequestId) return;

      const list = response.entries || [];
      lbTableBody.innerHTML = '';

      if (list.length === 0) {
        lbEmpty.classList.remove('hidden');
        lbEmpty.textContent = 'Пока нет результатов в этом режиме.';
        return;
      }

      lbEmpty.classList.add('hidden');
      list.forEach(entry => {
        const tr = document.createElement('tr');
        if (isHighlightedEntry(entry)) {
          tr.classList.add('highlight-rank');
        }

        appendCell(tr, `#${entry.rank}`, true);
        appendCell(tr, entry.nickname);
        appendCell(tr, formatTime(entry.totalTime));
        appendCell(tr, formatTime(entry.mainTime));
        appendCell(tr, String(entry.errors));
        appendCell(tr, formatDate(entry.timestamp));
        lbTableBody.appendChild(tr);
      });
    } catch (error) {
      if (requestId !== state.leaderboardRequestId) return;
      lbEmpty.classList.remove('hidden');
      lbEmpty.textContent = 'Не удалось загрузить рейтинг с сервера.';
    }
  }

  async function refreshPersonalBest() {
    const nickname = normalizeNickname(nicknameInput.value);
    const requestId = ++state.personalBestRequestId;

    if (!nickname) {
      personalBestValue.textContent = 'Введите ник, чтобы увидеть лучший результат.';
      return;
    }

    personalBestValue.textContent = 'Ищем ваш лучший результат...';

    try {
      const url = new URL(`${API_BASE_URL}/personal-best/${state.selectedDifficulty}`);
      url.searchParams.set('nickname', nickname);
      const response = await fetchJson(url.toString());
      if (requestId !== state.personalBestRequestId) return;

      if (!response.entry) {
        personalBestValue.textContent = 'Для этого ника ещё нет сохранённых рекордов.';
        return;
      }

      personalBestValue.textContent =
        `${formatTime(response.entry.totalTime)} • ошибок: ${response.entry.errors} • ` +
        `${formatDate(response.entry.timestamp)}`;
    } catch (error) {
      if (requestId !== state.personalBestRequestId) return;
      personalBestValue.textContent = 'Не удалось загрузить ваш рекорд с сервера.';
    }
  }

  function refreshNicknameState() {
    const nickname = normalizeNickname(nicknameInput.value);
    nicknameInput.classList.toggle('invalid', !nickname && nicknameInput.value.trim().length > 0);

    if (!nicknameInput.value.trim()) {
      nicknameHint.textContent = 'Ник нужен, чтобы сохранить рекорд в общий рейтинг.';
      nicknameHint.classList.remove('error');
      return;
    }

    if (!nickname) {
      nicknameHint.textContent = 'Допустимы буквы, цифры, пробел, точка, дефис и _.';
      nicknameHint.classList.add('error');
      return;
    }

    nicknameHint.textContent = `Ник сохранится как: ${nickname}`;
    nicknameHint.classList.remove('error');
  }

  function appendCell(row, text, strong) {
    const td = document.createElement('td');
    if (strong) {
      const element = document.createElement('strong');
      element.textContent = text;
      td.appendChild(element);
    } else {
      td.textContent = text;
    }
    row.appendChild(td);
  }

  function isHighlightedEntry(entry) {
    return Boolean(
      state.finalResult &&
      state.finalResult.timestamp === entry.timestamp &&
      state.finalResult.totalTime === entry.totalTime &&
      state.finalResult.nickname === entry.nickname
    );
  }

  function generateProblems(count) {
    const shuffledPool = shuffle(CanonicalPool).slice(0, count);
    return shuffledPool.map(pair => {
      let left = pair.left;
      let right = pair.right;
      if (left !== right && Math.random() < 0.5) {
        [left, right] = [right, left];
      }
      return { left, right, answer: left * right };
    });
  }

  function shuffle(array) {
    const arr = [...array];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  }

  function formatTime(ms) {
    const totalDecis = Math.floor(ms / 100);
    const deci = totalDecis % 10;
    const totalSecs = Math.floor(totalDecis / 10);
    const secs = totalSecs % 60;
    const mins = Math.floor(totalSecs / 60);
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}.${deci}`;
  }

  function formatDate(ts) {
    return new Date(ts).toLocaleDateString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: '2-digit',
    });
  }

  function normalizeNickname(value) {
    if (typeof value !== 'string') return '';
    const trimmed = value.trim().replace(/\s+/g, ' ');
    if (!trimmed) return '';
    const truncated = Array.from(trimmed).slice(0, MAX_NICKNAME_LENGTH).join('');
    return /^[\p{L}\p{N} _.\-]+$/u.test(truncated) ? truncated : '';
  }

  function loadSavedNickname() {
    try {
      return normalizeNickname(localStorage.getItem(NICKNAME_STORAGE_KEY) || '');
    } catch (error) {
      return '';
    }
  }

  async function fetchJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
  }

  function getApiBaseUrl() {
    if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
      return `${window.location.origin}/api`;
    }
    return 'http://93.88.203.16/api';
  }
})();
