import { useEffect } from 'react';

export function useLandingEffects() {
  useEffect(() => {
    const header = document.getElementById('header');
    const heroPhoto = document.getElementById('hero-photo');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let ticking = false;
    const update = () => {
      header?.classList.toggle('stuck', window.scrollY > 80);
      if (heroPhoto) heroPhoto.style.transform = !reducedMotion.matches && window.innerWidth > 720 && window.scrollY < window.innerHeight * 1.4 ? `translateY(${window.scrollY * 0.18}px)` : 'none';
      ticking = false;
    };
    const schedule = () => { if (!ticking) { window.requestAnimationFrame(update); ticking = true; } };
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', update);
    reducedMotion.addEventListener('change', update);
    update();
    let observer;
    if ('IntersectionObserver' in window && !reducedMotion.matches) {
      document.documentElement.classList.add('js-motion');
      observer = new IntersectionObserver((entries) => entries.forEach((entry) => {
        if (entry.isIntersecting) { entry.target.classList.add('visible'); observer.unobserve(entry.target); }
      }), { threshold: 0.12 });
      document.querySelectorAll('.reveal').forEach((element) => observer.observe(element));
    }
    return () => {
      window.removeEventListener('scroll', schedule); window.removeEventListener('resize', update); reducedMotion.removeEventListener('change', update); observer?.disconnect();
    };
  }, []);
}
