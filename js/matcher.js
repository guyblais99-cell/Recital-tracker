import { MATCH_THRESHOLD } from './config.js';

export { MATCH_THRESHOLD };

const SAMPLE = 64;
const MAX_SHIFT = 12;
const SHIFT_STEP = 2;
const HOLD_MS = 600;

export { HOLD_MS };

export function matchLiveToReference(liveCanvas, refImage) {
  const aspectW = liveCanvas.width;
  const aspectH = liveCanvas.height;
  const refCanvas = document.createElement('canvas');
  refCanvas.width = SAMPLE;
  refCanvas.height = SAMPLE;
  const refCtx = refCanvas.getContext('2d', { willReadFrequently: true });
  drawCropped(refCtx, refImage, aspectW, aspectH, SAMPLE, SAMPLE);
  const refGray = imageDataToGray(refCtx.getImageData(0, 0, SAMPLE, SAMPLE));

  const liveCtx = liveCanvas.getContext('2d', { willReadFrequently: true });
  const liveSmall = document.createElement('canvas');
  liveSmall.width = SAMPLE;
  liveSmall.height = SAMPLE;
  const lCtx = liveSmall.getContext('2d', { willReadFrequently: true });
  lCtx.drawImage(liveCanvas, 0, 0, SAMPLE, SAMPLE);
  const liveGray = imageDataToGray(lCtx.getImageData(0, 0, SAMPLE, SAMPLE));

  return matchGrids(refGray, liveGray);
}

function matchGrids(refGray, liveGray) {
  const refGrad = gradientGrid(refGray, SAMPLE, SAMPLE);
  const liveGrad = gradientGrid(liveGray, SAMPLE, SAMPLE);
  let bestScore = 0;
  let bestShiftX = 0;
  let bestShiftY = 0;
  for (let sy = -MAX_SHIFT; sy <= MAX_SHIFT; sy += SHIFT_STEP) {
    for (let sx = -MAX_SHIFT; sx <= MAX_SHIFT; sx += SHIFT_STEP) {
      const brightness = shiftedCorrelation(refGray, liveGray, SAMPLE, SAMPLE, sx, sy);
      const edges = shiftedCorrelation(refGrad, liveGrad, SAMPLE, SAMPLE, sx, sy);
      const score = Math.max(0, Math.min(1, brightness * 0.35 + edges * 0.65));
      if (score > bestScore) {
        bestScore = score;
        bestShiftX = sx;
        bestShiftY = sy;
      }
    }
  }
  return {
    score: bestScore,
    panX: Math.max(-1, Math.min(1, bestShiftX / MAX_SHIFT)),
    panY: Math.max(-1, Math.min(1, bestShiftY / MAX_SHIFT))
  };
}

function drawCropped(ctx, img, aspectW, aspectH, w, h) {
  const crop = centerAspectCrop(img.width, img.height, aspectW, aspectH);
  ctx.drawImage(img, crop.left, crop.top, crop.width, crop.height, 0, 0, w, h);
}

function centerAspectCrop(frameW, frameH, aspectW, aspectH) {
  const targetAspect = aspectW / aspectH;
  const frameAspect = frameW / frameH;
  let cw, ch;
  if (frameAspect > targetAspect) {
    ch = frameH;
    cw = Math.max(1, Math.floor(frameH * targetAspect));
  } else {
    cw = frameW;
    ch = Math.max(1, Math.floor(frameW / targetAspect));
  }
  return {
    left: Math.floor((frameW - cw) / 2),
    top: Math.floor((frameH - ch) / 2),
    width: cw,
    height: ch
  };
}

function imageDataToGray(data) {
  const out = new Float32Array(data.width * data.height);
  for (let i = 0; i < out.length; i++) {
    const p = i * 4;
    out[i] = (0.299 * data.data[p] + 0.587 * data.data[p + 1] + 0.114 * data.data[p + 2]) / 255;
  }
  return out;
}

function gradientGrid(gray, w, h) {
  const out = new Float32Array(gray.length);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const l = gray[y * w + Math.max(x - 1, 0)];
      const r = gray[y * w + Math.min(x + 1, w - 1)];
      const u = gray[Math.max(y - 1, 0) * w + x];
      const d = gray[Math.min(y + 1, h - 1) * w + x];
      out[y * w + x] = Math.hypot(r - l, d - u);
    }
  }
  return out;
}

function shiftedCorrelation(ref, live, w, h, shiftX, shiftY) {
  let sumA = 0, sumB = 0, count = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const lx = x + shiftX;
      const ly = y + shiftY;
      if (lx < 0 || lx >= w || ly < 0 || ly >= h) continue;
      sumA += ref[y * w + x];
      sumB += live[ly * w + lx];
      count++;
    }
  }
  if (count < (w * h) / 4) return 0;
  const meanA = sumA / count;
  const meanB = sumB / count;
  let num = 0, denA = 0, denB = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const lx = x + shiftX;
      const ly = y + shiftY;
      if (lx < 0 || lx >= w || ly < 0 || ly >= h) continue;
      const da = ref[y * w + x] - meanA;
      const db = live[ly * w + lx] - meanB;
      num += da * db;
      denA += da * da;
      denB += db * db;
    }
  }
  const den = Math.sqrt(denA * denB);
  if (den <= 1e-6) return 0;
  return Math.max(0, Math.min(1, num / den));
}

export function alignmentHintLabel(panX, panY, score) {
  if (score >= MATCH_THRESHOLD - 0.02) return null;
  const parts = [];
  if (Math.abs(panX) >= 0.22) parts.push(panX > 0 ? 'Move right →' : 'Move left ←');
  if (Math.abs(panY) >= 0.22) parts.push(panY > 0 ? 'Move down ↓' : 'Move up ↑');
  return parts.length ? parts.join('  ') : null;
}
