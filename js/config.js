export const firebaseConfig = {
  apiKey: 'AIzaSyC9xRYjrl9g7h_3g_BnRoea7FfcvIlCCa0',
  authDomain: 'recital-tracker.firebaseapp.com',
  databaseURL: 'https://recital-tracker-default-rtdb.firebaseio.com',
  projectId: 'recital-tracker',
  storageBucket: 'recital-tracker.firebasestorage.app',
  messagingSenderId: '1039061307164',
  appId: '1:1039061307164:web:060356cd7bb14bdd91f4d9'
};

export const ARRIVED_RADIUS_M = 12;
export const RADAR_MAX_RANGE_M = 170;
export const RADAR_GREEN_ONLY_M = 20;
export const RADAR_RING_COUNT = 7;
export const MATCH_THRESHOLD = 0.26;
export const HOLD_MS = 600;

export const PEEL_THRESHOLDS_M = [
  RADAR_MAX_RANGE_M * 200 / 260,
  RADAR_MAX_RANGE_M * 140 / 260,
  RADAR_MAX_RANGE_M * 95 / 260,
  RADAR_MAX_RANGE_M * 65 / 260,
  RADAR_MAX_RANGE_M * 42 / 260,
  RADAR_MAX_RANGE_M * 28 / 260
];
