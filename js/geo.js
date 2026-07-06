import {
  ARRIVED_RADIUS_M,
  PEEL_THRESHOLDS_M,
  RADAR_GREEN_ONLY_M,
  RADAR_MAX_RANGE_M,
  RADAR_RING_COUNT
} from './config.js';

export const HOST_PIN_IDEAL_ACCURACY_M = 12;
export const PLAYER_RELIABLE_ACCURACY_M = 50;

export function distanceMeters(lat1, lon1, lat2, lon2) {
  const r = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function radarPeeledBands(distanceM, arrived) {
  if (arrived) return RADAR_RING_COUNT;
  let peeled = 0;
  for (const t of PEEL_THRESHOLDS_M) {
    if (distanceM < t) peeled++;
    else break;
  }
  return Math.min(Math.max(peeled, 0), RADAR_RING_COUNT - 1);
}

export function isRadarGreenOnly(distanceM, arrived) {
  return arrived || distanceM <= RADAR_GREEN_ONLY_M;
}

export function playerGpsStrength(accuracyM) {
  if (accuracyM <= HOST_PIN_IDEAL_ACCURACY_M) return 1;
  if (accuracyM >= PLAYER_RELIABLE_ACCURACY_M) return 0.08;
  const span = PLAYER_RELIABLE_ACCURACY_M - HOST_PIN_IDEAL_ACCURACY_M;
  return Math.max(0.08, Math.min(1, 1 - (accuracyM - HOST_PIN_IDEAL_ACCURACY_M) / span));
}

export function closerFurtherLabel(distanceM, prev, gpsReliable, arrived) {
  if (arrived) return "You're here — ready to scan!";
  if (!gpsReliable) return 'Finding GPS signal…';
  if (prev == null) return 'Start walking';
  if (distanceM < prev - 1.5) return 'Getting closer';
  if (distanceM > prev + 1.5) return 'Getting further';
  return 'Hold steady';
}

export function formatDistance(m) {
  if (m >= 1000) return `${(m / 1000).toFixed(1)} km`;
  return `${Math.round(m)} m`;
}

export function buildNavigator(fix, targetLat, targetLon, prevDistance) {
  const dist = distanceMeters(fix.lat, fix.lon, targetLat, targetLon);
  const reliable = fix.accuracyM <= PLAYER_RELIABLE_ACCURACY_M;
  const arrived = reliable && dist <= ARRIVED_RADIUS_M;
  return { distanceM: dist, accuracyM: fix.accuracyM, gpsReliable: reliable, isArrived: arrived, trendLabel: closerFurtherLabel(dist, prevDistance, reliable, arrived) };
}

const RING_COLORS = [
  [255, 45, 45],
  [255, 107, 0],
  [255, 208, 0],
  [170, 255, 0],
  [57, 255, 20]
];

function stepColor(step) {
  const t = step / Math.max(RADAR_RING_COUNT - 1, 1);
  if (t < 0.25) return lerpColor(RING_COLORS[0], RING_COLORS[1], t / 0.25);
  if (t < 0.5) return lerpColor(RING_COLORS[1], RING_COLORS[2], (t - 0.25) / 0.25);
  if (t < 0.75) return lerpColor(RING_COLORS[2], RING_COLORS[3], (t - 0.5) / 0.25);
  return lerpColor(RING_COLORS[3], RING_COLORS[4], (t - 0.75) / 0.25);
}

function lerpColor(a, b, t) {
  const u = Math.max(0, Math.min(1, t));
  return `rgb(${Math.round(a[0] + (b[0] - a[0]) * u)},${Math.round(a[1] + (b[1] - a[1]) * u)},${Math.round(a[2] + (b[2] - a[2]) * u)})`;
}

/** Draw proximity radar on canvas (matches Android behavior). */
export function drawRadar(ctx, size, distanceM, arrived, pulse) {
  const cx = size / 2;
  const cy = size / 2;
  const maxR = size * 0.47;
  const greenOnly = isRadarGreenOnly(distanceM, arrived);
  const peeled = greenOnly ? RADAR_RING_COUNT : radarPeeledBands(distanceM, arrived);

  ctx.fillStyle = '#2a1245';
  ctx.beginPath();
  ctx.arc(cx, cy, maxR, 0, Math.PI * 2);
  ctx.fill();

  if (!greenOnly && peeled < RADAR_RING_COUNT) {
    const active = Math.min(Math.max(peeled, 0), RADAR_RING_COUNT - 1);
    const outerR = (maxR * (RADAR_RING_COUNT - active)) / RADAR_RING_COUNT;
    const innerR = (maxR * (RADAR_RING_COUNT - active - 1)) / RADAR_RING_COUNT;
    ctx.globalAlpha = 0.78;
    ctx.fillStyle = stepColor(active);
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
    ctx.arc(cx, cy, Math.max(innerR, 0), 0, Math.PI * 2, true);
    ctx.fill();
    ctx.globalAlpha = 1;
  }

  if (greenOnly || arrived) {
    const baseR = (maxR / RADAR_RING_COUNT) * 0.55;
    const pulseR = baseR * (0.85 + pulse * 0.35);
    ctx.strokeStyle = `rgba(57,255,20,${0.7 + pulse * 0.3})`;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.arc(cx, cy, pulseR, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = '#39ff14';
    ctx.beginPath();
    ctx.arc(cx, cy, 5 + pulse * 3, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.strokeStyle = 'rgba(255,255,255,0.2)';
  ctx.lineWidth = 1.5;
  for (let i = 1; i <= RADAR_RING_COUNT; i++) {
    const r = (maxR * i) / RADAR_RING_COUNT;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.stroke();
  }
}

export function activeBandStep(distanceM, arrived) {
  if (isRadarGreenOnly(distanceM, arrived)) return RADAR_RING_COUNT - 1;
  return Math.min(radarPeeledBands(distanceM, arrived), RADAR_RING_COUNT - 1);
}

export function bandColorForDistance(distanceM, arrived) {
  if (isRadarGreenOnly(distanceM, arrived)) return '#39ff14';
  return stepColor(activeBandStep(distanceM, arrived));
}
