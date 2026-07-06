/** Web Speech API — read clues aloud for kids. */
let preferredVoice = null;

function pickVoice() {
  if (preferredVoice) return preferredVoice;
  const voices = window.speechSynthesis?.getVoices() || [];
  preferredVoice =
    voices.find((v) => v.lang.startsWith('en') && /female|samantha|karen|zira/i.test(v.name)) ||
    voices.find((v) => v.lang.startsWith('en')) ||
    voices[0] ||
    null;
  return preferredVoice;
}

if (typeof window !== 'undefined' && window.speechSynthesis) {
  window.speechSynthesis.onvoiceschanged = () => { preferredVoice = null; };
}

export function speak(text, { interrupt = true } = {}) {
  const t = (text || '').trim();
  if (!t || !window.speechSynthesis) return false;
  if (interrupt) window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(t);
  u.rate = 0.9;
  u.pitch = 1.05;
  const v = pickVoice();
  if (v) u.voice = v;
  window.speechSynthesis.speak(u);
  return true;
}

export function stopSpeaking() {
  window.speechSynthesis?.cancel();
}

export function isSpeaking() {
  return window.speechSynthesis?.speaking === true;
}
