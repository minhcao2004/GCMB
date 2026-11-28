import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { authApi } from '../../auth/services/authApi';
import { foodOrderApi } from '../services/foodOrderApi';

const pollInterval = 5000;

function playChime(context) {
  const now = context.currentTime;
  [[880, 0], [660, 0.16], [880, 0.32]].forEach(([frequency, offset]) => {
    const startAt = now + offset;
    const oscillator = context.createOscillator();
    const volume = context.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(frequency, startAt);
    volume.gain.setValueAtTime(0.0001, startAt);
    volume.gain.exponentialRampToValueAtTime(0.16, startAt + 0.015);
    volume.gain.exponentialRampToValueAtTime(0.0001, startAt + 0.14);
    oscillator.connect(volume);
    volume.connect(context.destination);
    oscillator.start(startAt);
    oscillator.stop(startAt + 0.15);
  });
}

export default function ReceptionOrderSound() {
  const { pathname } = useLocation();
  const receptionistRoute = pathname === '/le-tan' || pathname.startsWith('/le-tan/');

  useEffect(() => {
    if (!receptionistRoute) return undefined;

    let active = true;
    let inFlight = false;
    let knownIds = null;
    let pendingSound = false;
    let timer;
    let context;

    const play = () => {
      if (!context || context.state !== 'running') { pendingSound = true; return; }
      pendingSound = false;
      playChime(context);
    };
    const resume = () => {
      if (!context || !active) return;
      if (context.state === 'running') { if (pendingSound) play(); return; }
      context.resume().then(() => { if (active && pendingSound) play(); }).catch(() => {});
    };
    const poll = async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const orders = await foodOrderApi.staffOrders('PENDING');
        if (!active) return;
        if (knownIds && orders.some((order) => !knownIds.has(order.id))) play();
        if (!knownIds) knownIds = new Set();
        orders.forEach((order) => knownIds.add(order.id));
      } catch {
        // Thử lại ở lần thăm dò kế tiếp.
      } finally { inFlight = false; }
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') { resume(); poll(); }
    };

    authApi.me().then((identity) => {
      if (!active || identity.role !== 'RECEPTIONIST') return;
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (AudioContextClass) {
        try { context = new AudioContextClass(); resume(); } catch { /* cảnh báo trên màn vẫn hoạt động */ }
      }
      document.addEventListener('pointerdown', resume);
      document.addEventListener('keydown', resume);
      document.addEventListener('visibilitychange', onVisible);
      poll();
      timer = window.setInterval(poll, pollInterval);
    }).catch(() => {});

    return () => {
      active = false;
      window.clearInterval(timer);
      document.removeEventListener('pointerdown', resume);
      document.removeEventListener('keydown', resume);
      document.removeEventListener('visibilitychange', onVisible);
      context?.close().catch(() => {});
    };
  }, [receptionistRoute]);

  return null;
}
