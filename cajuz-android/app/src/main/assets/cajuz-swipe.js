/* Cajuz Android 0.2.1: swipe right to open the Cajuz mobile menu.
 * Uses the existing Cajuz drawer toggle so ARIA, scrim, focus and navigation
 * continue to be managed by the WordPress plugin, not reimplemented here.
 */
(function () {
  'use strict';
  if (window.__cajuzAndroidSwipeMenuV1) return;
  window.__cajuzAndroidSwipeMenuV1 = true;

  let start = null;
  const mobile = () => window.matchMedia('(max-width:700px)').matches;
  const drawer = () => document.getElementById('cajuz-mobile-drawer');
  const toggle = () => document.querySelector('[data-cajuz-drawer-toggle]');
  const excluded = [
    'input', 'textarea', 'select', '[contenteditable="true"]',
    '.cajuz-task-kanban', '.cajuz-month-mobile', '.cajuz-week-card',
    '.cajuz-global-nav', '.cajuz-media-preview', '.cajuz-section-tabs',
    '.cajuz-planning-controls', '.swiper', '.slick-slider',
    '[role="slider"]', '[data-no-swipe-menu]'
  ].join(',');

  const horizontalScroller = (target) => {
    for (let el = target; el && el !== document.body; el = el.parentElement) {
      const style = window.getComputedStyle(el);
      if (/(auto|scroll)/.test(style.overflowX) && el.scrollWidth > el.clientWidth + 8) return true;
    }
    return false;
  };

  document.addEventListener('touchstart', (e) => {
    start = null;
    if (!mobile() || e.touches.length !== 1 || !(e.target instanceof Element)) return;
    const menu = drawer();
    if (!menu || !toggle()) return;
    if (e.target.closest(excluded) || horizontalScroller(e.target)) return;
    const open = !menu.hidden;
    const x = e.touches[0].clientX;
    // Opening works from the left side of the content area, not just 2px
    // from the Android system-back-gesture edge.
    if (!open && x > window.innerWidth * 0.48) return;
    if (open && !e.target.closest('#cajuz-mobile-drawer')) return;
    start = { x, y: e.touches[0].clientY, at: Date.now(), open };
  }, { passive: true, capture: true });

  document.addEventListener('touchend', (e) => {
    const gesture = start;
    start = null;
    if (!gesture || !e.changedTouches.length || Date.now() - gesture.at > 1200) return;
    const dx = e.changedTouches[0].clientX - gesture.x;
    const dy = e.changedTouches[0].clientY - gesture.y;
    if (Math.abs(dx) < 85 || Math.abs(dy) > 58 || Math.abs(dx) < Math.abs(dy) * 1.6) return;
    const menu = drawer(), control = toggle();
    if (!menu || !control) return;
    if (!gesture.open && dx > 0 && menu.hidden) {
      if (e.cancelable) e.preventDefault();
      e.stopImmediatePropagation();
      control.click();
    } else if (gesture.open && dx < 0 && !menu.hidden) {
      if (e.cancelable) e.preventDefault();
      e.stopImmediatePropagation();
      const close = menu.querySelector('[data-cajuz-drawer-close]');
      if (close) close.click();
      else control.click();
    }
  }, { passive: false, capture: true });

  document.addEventListener('touchcancel', () => { start = null; }, { passive: true, capture: true });
}());
