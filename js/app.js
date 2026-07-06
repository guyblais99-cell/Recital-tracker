/* Scavenger Hunt PWA — player flow + TTS */
import { MATCH_THRESHOLD, HOLD_MS } from './config.js';
import { HuntRepository, nextCheckpoint, isHuntComplete, completedCount } from './repo.js';
import {
  buildNavigator,
  drawRadar,
  bandColorForDistance,
  formatDistance,
  playerGpsStrength,
  isRadarGreenOnly
} from './geo.js';
import { matchLiveToReference, alignmentHintLabel } from './matcher.js';
import { speak, stopSpeaking } from './tts.js';

const SESSION_KEY = 'scavenger_player_session';

const state = {
  screen: 'auth',
  hunt: null,
  teamId: null,
  team: null,
  activeCheckpoint: null,
  revealedClue: null,
  nav: null,
  prevDistance: null,
  matchScore: 0,
  rawScore: 0,
  panX: 0,
  panY: 0,
  holdStart: 0,
  alignUnlocked: false,
  gpsWatch: null,
  huntUnsub: null,
  alignLoop: null,
  alignStream: null,
  radarPulse: 0,
  radarAnim: null
};

let repo;
let refImage = null;

const $ = (id) => document.getElementById(id);

function showScreen(name) {
  state.screen = name;
  document.querySelectorAll('.screen').forEach((el) => {
    el.classList.toggle('hidden', el.dataset.screen !== name);
  });
  stopSpeaking();
  if (name !== 'align') stopAlign();
  if (name !== 'player') stopGpsWatch();
  if (name === 'player') startPlayerScreen();
  if (name === 'align') startAlign();
  if (name === 'clue') startClueScreen();
}

function setStatus(msg, isError = false) {
  const el = $('status-msg');
  if (!el) return;
  el.textContent = msg || '';
  el.classList.toggle('error', isError);
}

function saveSession() {
  if (state.hunt?.huntId && state.teamId) {
    localStorage.setItem(SESSION_KEY, JSON.stringify({ huntId: state.hunt.huntId, teamId: state.teamId }));
  }
}

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null');
  } catch {
    return null;
  }
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

function refreshTeam() {
  if (!state.hunt || !state.teamId) {
    state.team = null;
    return;
  }
  state.team = state.hunt.teams.find((t) => t.teamId === state.teamId) || null;
}

function subscribeHunt(huntId) {
  if (state.huntUnsub) state.huntUnsub();
  state.huntUnsub = repo.observeHunt(huntId, (snap) => {
    state.hunt = snap;
    refreshTeam();
    renderPlayer();
  });
}

async function joinHunt(code, teamName) {
  setStatus('');
  try {
    const huntId = await repo.resolveJoinCode(code);
    if (!huntId) {
      setStatus('Invalid join code', true);
      return;
    }
    const teamId = await repo.joinHunt(huntId, teamName);
    state.teamId = teamId;
    saveSession();
    subscribeHunt(huntId);
    showScreen('player');
  } catch (e) {
    setStatus(e.message || 'Could not join', true);
  }
}

function startGpsWatch(cp) {
  stopGpsWatch();
  if (!cp?.latitude || !cp?.longitude || !navigator.geolocation) return;
  state.prevDistance = null;
  state.gpsWatch = navigator.geolocation.watchPosition(
    (pos) => {
      const fix = { lat: pos.coords.latitude, lon: pos.coords.longitude, accuracyM: pos.coords.accuracy };
      state.nav = buildNavigator(fix, cp.latitude, cp.longitude, state.prevDistance);
      state.prevDistance = state.nav.distanceM;
      updateGpsBar(pos.coords.accuracy);
      renderRadar();
      updateArriveButton();
    },
    () => updateGpsBar(null),
    { enableHighAccuracy: true, maximumAge: 500, timeout: 15000 }
  );
}

function stopGpsWatch() {
  if (state.gpsWatch != null) {
    navigator.geolocation.clearWatch(state.gpsWatch);
    state.gpsWatch = null;
  }
}

function updateGpsBar(accuracyM) {
  const bar = $('gps-bar-fill');
  const label = $('gps-bar');
  if (!bar) return;
  if (accuracyM == null) {
    bar.style.width = '30%';
    bar.className = 'gps-bar-fill weak';
    return;
  }
  const s = playerGpsStrength(accuracyM);
  bar.style.width = `${Math.round(s * 100)}%`;
  bar.className = 'gps-bar-fill ' + (s >= 0.72 ? 'good' : s >= 0.45 ? 'fair' : s >= 0.22 ? 'ok' : 'weak');
}

function startRadarAnim() {
  if (state.radarAnim) return;
  const tick = () => {
    state.radarPulse = (Date.now() % 1000) / 1000;
    renderRadar();
    state.radarAnim = requestAnimationFrame(tick);
  };
  state.radarAnim = requestAnimationFrame(tick);
}

function stopRadarAnim() {
  if (state.radarAnim) {
    cancelAnimationFrame(state.radarAnim);
    state.radarAnim = null;
  }
}

function renderRadar() {
  const canvas = $('radar-canvas');
  if (!canvas || !state.nav) return;
  const size = canvas.width;
  const ctx = canvas.getContext('2d');
  drawRadar(ctx, size, state.nav.distanceM, state.nav.isArrived, state.radarPulse);
  const distEl = $('radar-distance');
  const trendEl = $('radar-trend');
  if (distEl) {
    distEl.textContent = formatDistance(state.nav.distanceM);
    distEl.style.color = bandColorForDistance(state.nav.distanceM, state.nav.isArrived);
  }
  if (trendEl) {
    trendEl.textContent = state.nav.trendLabel;
    trendEl.style.color = bandColorForDistance(state.nav.distanceM, state.nav.isArrived);
  }
}

function updateArriveButton() {
  const btn = $('btn-arrive');
  const cp = nextCheckpoint(state.hunt, state.team);
  const hasGps = cp?.latitude != null && cp?.longitude != null;
  const arrived = !hasGps || state.nav?.isArrived;
  if (btn) btn.classList.toggle('hidden', !arrived);
}

function renderPlayer() {
  if (!state.hunt) return;
  const cp = nextCheckpoint(state.hunt, state.team);
  const complete = isHuntComplete(state.hunt, state.team);

  $('hunt-name-banner').textContent = state.hunt.meta.name || 'Hunt';
  const done = completedCount(state.team || { completedCheckpointIds: {} });
  $('progress-label').textContent = `${done} of ${state.hunt.checkpoints.length} checkpoints complete`;
  $('progress-bar').style.width = `${(done / Math.max(state.hunt.checkpoints.length, 1)) * 100}%`;

  if (complete) {
    $('player-active').classList.add('hidden');
    $('player-complete').classList.remove('hidden');
    stopGpsWatch();
    stopRadarAnim();
    return;
  }
  $('player-active').classList.remove('hidden');
  $('player-complete').classList.add('hidden');

  if (cp) {
    $('direction-clue-text').textContent = cp.hintText || '(No direction clue)';
    $('direction-clue-wrap').classList.remove('hidden');
    startGpsWatch(cp);
    startRadarAnim();
    updateArriveButton();
  } else {
    $('direction-clue-wrap').classList.add('hidden');
  }

  const list = $('checkpoint-list');
  list.innerHTML = '';
  state.hunt.checkpoints.forEach((c, i) => {
    const doneCp = state.team?.completedCheckpointIds[c.id];
    const current = cp?.id === c.id;
    const li = document.createElement('li');
    li.className = 'checkpoint-item' + (doneCp ? ' done' : current ? ' current' : '');
    li.innerHTML = `<span class="cp-num">${i + 1}</span><span class="cp-title">${escapeHtml(c.title)}</span><span class="cp-status">${doneCp ? '✓' : current ? '→' : '·'}</span>`;
    list.appendChild(li);
  });
}

function startPlayerScreen() {
  renderPlayer();
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

async function startAlign() {
  const cp = state.activeCheckpoint;
  if (!cp) return;
  state.alignUnlocked = false;
  state.matchScore = 0;
  state.rawScore = 0;
  state.holdStart = 0;
  $('align-title').textContent = cp.title;
  refImage = new Image();
  refImage.src = cp.imageBase64 ? `data:image/jpeg;base64,${cp.imageBase64}` : '';

  const video = $('align-video');
  const ghost = $('align-ghost');
  ghost.src = refImage.src;

  try {
    state.alignStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } },
      audio: false
    });
    video.srcObject = state.alignStream;
    await video.play();
  } catch {
    $('align-status').textContent = 'Camera permission required';
    return;
  }

  const analyzeCanvas = $('align-analyze');
  const loop = () => {
    if (state.screen !== 'align' || !refImage.complete) {
      state.alignLoop = requestAnimationFrame(loop);
      return;
    }
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    if (vw && vh) {
      analyzeCanvas.width = vw;
      analyzeCanvas.height = vh;
      const ctx = analyzeCanvas.getContext('2d');
      ctx.drawImage(video, 0, 0);
      const result = matchLiveToReference(analyzeCanvas, refImage);
      state.rawScore = result.score;
      state.matchScore = state.matchScore * 0.5 + result.score * 0.5;
      state.panX = state.panX * 0.55 + result.panX * 0.45;
      state.panY = state.panY * 0.55 + result.panY * 0.45;
      updateAlignUi();
    }
    state.alignLoop = requestAnimationFrame(loop);
  };
  state.alignLoop = requestAnimationFrame(loop);
}

function updateAlignUi() {
  const pct = Math.round(state.matchScore * 100);
  const need = Math.round(MATCH_THRESHOLD * 100);
  const hint = alignmentHintLabel(state.panX, state.panY, state.matchScore);
  const status = $('align-status');
  const bar = $('align-bar');
  const arrows = $('align-arrows');

  bar.style.width = `${Math.min(100, state.matchScore * 100)}%`;

  if (state.alignUnlocked) {
    status.textContent = 'Matched! Unlocking clue…';
  } else if (state.matchScore >= MATCH_THRESHOLD) {
    if (!state.holdStart) state.holdStart = Date.now();
    const held = Date.now() - state.holdStart;
    if (held >= HOLD_MS) {
      onAligned();
      return;
    }
    status.textContent = `Hold steady… ${Math.round((held / HOLD_MS) * 100)}%`;
  } else if (hint) {
    status.textContent = hint;
  } else if (pct >= need - 10) {
    status.textContent = `Almost there — ${pct}%`;
  } else {
    status.textContent = `Match the ghost — ${pct}%`;
  }

  arrows.innerHTML = '';
  if (state.matchScore < MATCH_THRESHOLD + 0.05) {
    if (state.panX <= -0.22) arrows.innerHTML += '<span class="arrow left">←</span>';
    if (state.panX >= 0.22) arrows.innerHTML += '<span class="arrow right">→</span>';
    if (state.panY <= -0.22) arrows.innerHTML += '<span class="arrow up">↑</span>';
    if (state.panY >= 0.22) arrows.innerHTML += '<span class="arrow down">↓</span>';
  }
}

async function onAligned() {
  if (state.alignUnlocked) return;
  state.alignUnlocked = true;
  const cp = state.activeCheckpoint;
  try {
    await repo.completeCheckpoint(state.hunt.huntId, state.teamId, cp.id);
    state.revealedClue = cp.clueText;
    showScreen('clue');
  } catch (e) {
    $('align-status').textContent = e.message || 'Could not save';
    state.alignUnlocked = false;
  }
}

function stopAlign() {
  if (state.alignLoop) cancelAnimationFrame(state.alignLoop);
  state.alignLoop = null;
  if (state.alignStream) {
    state.alignStream.getTracks().forEach((t) => t.stop());
    state.alignStream = null;
  }
}

function startClueScreen() {
  const text = state.revealedClue || '';
  $('reward-clue-text').textContent = text;
  $('reward-title').textContent = state.activeCheckpoint?.title || '';
  speak(text);
}

function wireEvents() {
  $('auth-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = $('auth-email').value;
    const password = $('auth-password').value;
    const signUp = $('auth-mode').dataset.signup === '1';
    setStatus('');
    try {
      if (signUp) await repo.signUp(email, password);
      else await repo.signIn(email, password);
    } catch (err) {
      setStatus(err.message, true);
    }
  });

  $('toggle-auth').addEventListener('click', () => {
    const mode = $('auth-mode');
    const signUp = mode.dataset.signup !== '1';
    mode.dataset.signup = signUp ? '1' : '0';
    $('auth-submit').textContent = signUp ? 'Create account' : 'Sign in';
    $('toggle-auth').textContent = signUp ? 'Already have an account? Sign in' : 'Need an account? Sign up';
  });

  $('join-form').addEventListener('submit', (e) => {
    e.preventDefault();
    joinHunt($('join-code').value, $('team-name').value);
  });

  $('btn-resume')?.addEventListener('click', () => {
    const s = loadSession();
    if (s?.huntId) {
      state.teamId = s.teamId;
      subscribeHunt(s.huntId);
      showScreen('player');
    }
  });

  $('btn-signout').addEventListener('click', () => {
    if (state.huntUnsub) state.huntUnsub();
    clearSession();
    repo.signOut();
  });

  $('btn-leave').addEventListener('click', () => {
    stopGpsWatch();
    stopRadarAnim();
    showScreen('home');
  });

  $('btn-play-clue').addEventListener('click', () => {
    const cp = nextCheckpoint(state.hunt, state.team);
    if (cp?.hintText) speak(cp.hintText);
  });

  $('btn-arrive').addEventListener('click', () => {
    const cp = nextCheckpoint(state.hunt, state.team);
    if (cp) {
      state.activeCheckpoint = cp;
      showScreen('align');
    }
  });

  $('btn-align-back').addEventListener('click', () => {
    state.activeCheckpoint = null;
    showScreen('player');
  });

  $('btn-align-skip').addEventListener('click', () => onAligned());

  $('btn-play-reward').addEventListener('click', () => speak(state.revealedClue));

  $('btn-clue-continue').addEventListener('click', () => {
    state.activeCheckpoint = null;
    state.revealedClue = null;
    showScreen('player');
  });
}

function init() {
  if (!firebase.apps.length) firebase.initializeApp(window.__FIREBASE_CONFIG__);
  repo = new HuntRepository(firebase);
  wireEvents();

  const session = loadSession();
  if (session?.huntId) $('btn-resume').classList.remove('hidden');

  repo.onAuth((user) => {
    if (user) {
      $('user-email').textContent = user.email || '';
      showScreen('home');
    } else {
      showScreen('auth');
    }
  });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
}

init();
