export function parseCheckpoint(id, map) {
  if (!map) return null;
  return {
    id: map.id || id,
    order: Number(map.order) || 0,
    title: map.title || '',
    clueText: map.clueText || '',
    hintText: map.hintText || '',
    imageBase64: map.imageBase64 || '',
    latitude: map.latitude != null ? Number(map.latitude) : null,
    longitude: map.longitude != null ? Number(map.longitude) : null
  };
}

export function parseTeam(teamId, map) {
  if (!map) return { teamId, teamName: '', playerUid: '', joinedAt: 0, completedCheckpointIds: {} };
  const completed = map.completedCheckpointIds || {};
  const parsed = {};
  Object.keys(completed).forEach((k) => {
    parsed[k] = completed[k] === true || completed[k] === 1 || completed[k] === 'true';
  });
  return {
    teamId,
    teamName: map.teamName || '',
    playerUid: map.playerUid || '',
    playerEmail: map.playerEmail || '',
    joinedAt: Number(map.joinedAt) || 0,
    completedCheckpointIds: parsed,
    lastCompletedAt: Number(map.lastCompletedAt) || 0
  };
}

export function parseHuntSnapshot(huntId, snap) {
  if (!snap.exists()) return null;
  const val = snap.val();
  const meta = val.meta || {};
  const checkpoints = [];
  const cps = val.checkpoints || {};
  Object.keys(cps).forEach((key) => {
    const cp = parseCheckpoint(key, cps[key]);
    if (cp) checkpoints.push(cp);
  });
  checkpoints.sort((a, b) => a.order - b.order);
  const teams = [];
  const tmap = val.teams || {};
  Object.keys(tmap).forEach((key) => teams.push(parseTeam(key, tmap[key])));
  teams.sort((a, b) => completedCount(b) - completedCount(a));
  return {
    huntId,
    meta: {
      name: meta.name || '',
      joinCode: meta.joinCode || '',
      hostUid: meta.hostUid || '',
      status: meta.status || 'open'
    },
    checkpoints,
    teams
  };
}

export function completedCount(team) {
  return Object.values(team.completedCheckpointIds || {}).filter(Boolean).length;
}

export function nextCheckpoint(hunt, team) {
  if (!hunt?.checkpoints?.length) return null;
  const done = team?.completedCheckpointIds || {};
  return hunt.checkpoints.find((cp) => !done[cp.id]) || null;
}

export function isHuntComplete(hunt, team) {
  if (!hunt?.checkpoints?.length || !team) return false;
  return hunt.checkpoints.every((cp) => team.completedCheckpointIds[cp.id]);
}

export class HuntRepository {
  constructor(firebase) {
    this.auth = firebase.auth();
    this.db = firebase.database();
    this.huntsRef = this.db.ref('recitals');
    this.joinCodesRef = this.db.ref('joinCodes');
  }

  get currentUser() {
    return this.auth.currentUser;
  }

  onAuth(cb) {
    return this.auth.onAuthStateChanged(cb);
  }

  signIn(email, password) {
    return this.auth.signInWithEmailAndPassword(email.trim(), password);
  }

  signUp(email, password) {
    return this.auth.createUserWithEmailAndPassword(email.trim(), password);
  }

  signOut() {
    return this.auth.signOut();
  }

  resolveJoinCode(code) {
    return this.joinCodesRef
      .child(code.trim().toUpperCase())
      .once('value')
      .then((s) => s.val());
  }

  async joinHunt(huntId, teamName) {
    const user = this.currentUser;
    if (!user) throw new Error('Sign in required');
    const teamsRef = this.huntsRef.child(huntId).child('teams');
    const snap = await teamsRef.once('value');
    const normalized = teamName.trim();
    let existingId = null;
    snap.forEach((child) => {
      const t = child.val();
      if (t?.playerUid === user.uid && (t.teamName || '').trim().toLowerCase() === normalized.toLowerCase()) {
        existingId = child.key;
      }
    });
    if (existingId) return existingId;
    const teamId = teamsRef.push().key;
    await teamsRef.child(teamId).set({
      teamName: normalized,
      playerUid: user.uid,
      playerEmail: user.email || '',
      joinedAt: Date.now(),
      completedCheckpointIds: {},
      lastCompletedAt: 0
    });
    return teamId;
  }

  observeHunt(huntId, cb) {
    const ref = this.huntsRef.child(huntId);
    const listener = (snap) => cb(parseHuntSnapshot(huntId, snap));
    ref.on('value', listener);
    return () => ref.off('value', listener);
  }

  completeCheckpoint(huntId, teamId, checkpointId) {
    return this.huntsRef.child(huntId).child('teams').child(teamId).update({
      [`completedCheckpointIds/${checkpointId}`]: true,
      lastCompletedAt: Date.now()
    });
  }
}
