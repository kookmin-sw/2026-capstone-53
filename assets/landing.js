/* ════════════════════════════════════════════════════════════════════
 * 오늘어디 (TodayWay) — landing v4 motion
 *
 * Award-site 스택:
 *  · Lenis 부드러운 스크롤 (관성 + lerp)
 *  · GSAP + ScrollTrigger — scrubbed timeline 으로 모든 모션이 스크롤에 묶임
 *  · sticky stage 안에서 6 frame 이 scroll progress 따라 cross-fade
 *  · 추가: 단어 reveal, number countup, walker sprite, route runner motionPath
 * ════════════════════════════════════════════════════════════════════ */

(() => {
  "use strict";

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ─────────────── Sprite helper ─────────────── */
  function makeSprite(host, interval = 130) {
    if (!host) return { play() {}, stop() {} };
    const frames = $$("img", host);
    if (frames.length < 2) return { play() {}, stop() {} };
    let idx = 0, t = null, on = false;
    const show = (i) => {
      frames[idx].classList.remove("active");
      idx = ((i % frames.length) + frames.length) % frames.length;
      frames[idx].classList.add("active");
    };
    return {
      play() { if (on || reduce) return; on = true; t = setInterval(() => show(idx + 1), interval); },
      stop() { if (!on) return; on = false; if (t) clearInterval(t); },
      get on() { return on; },
    };
  }

  function setup() {
    const hasGSAP = typeof window.gsap !== "undefined";
    const hasST = hasGSAP && typeof window.ScrollTrigger !== "undefined";
    if (hasST) window.gsap.registerPlugin(window.ScrollTrigger);

    /* ─────────────── Topbar scrolled ─────────────── */
    const topbar = $("#topbar");
    const onTopbar = () => topbar && topbar.classList.toggle("scrolled", window.scrollY > 16);
    addEventListener("scroll", onTopbar, { passive: true });
    onTopbar();

    /* ─────────────── Scroll progress bar (GPU transform) ─────────────── */
    const scrollProg = $("#scrollProg");
    let cachedDocH = 0;
    function refreshDocH() { cachedDocH = document.documentElement.scrollHeight - innerHeight; }
    function updateProg() {
      if (!scrollProg) return;
      const p = cachedDocH > 0 ? window.scrollY / cachedDocH : 0;
      scrollProg.style.transform = `scaleX(${p})`;
    }
    addEventListener("scroll", updateProg, { passive: true });
    addEventListener("resize", () => { refreshDocH(); updateProg(); });
    refreshDocH();
    updateProg();

    /* ─────────────── Left rail walker ─────────────── */
    const railChar = $("#railChar");
    const railLine = $(".rail-line");
    const railProgress = $(".rail-progress");
    const walk = makeSprite(railChar, 140);
    let walkStopT = null, lastY = -1;
    let cachedLineH = 0;
    function refreshRail() {
      cachedLineH = railLine ? railLine.offsetHeight : 0;
    }
    function updateRail() {
      if (!railLine || cachedLineH === 0) return;
      const pct = cachedDocH > 0 ? clamp(window.scrollY / cachedDocH, 0, 1) : 0;
      if (railChar) railChar.style.transform = `translateY(${pct * cachedLineH}px)`;
      // 진행 fill 도 transform scale 로 (paint 안 일으킴)
      if (railLine) railLine.style.setProperty("--p", (pct * 100) + "%");
      if (Math.abs(window.scrollY - lastY) > 1) {
        walk.play();
        if (walkStopT) clearTimeout(walkStopT);
        walkStopT = setTimeout(() => walk.stop(), 280);
        lastY = window.scrollY;
      }
    }
    addEventListener("scroll", updateRail, { passive: true });
    addEventListener("resize", () => { refreshDocH(); refreshRail(); updateRail(); });
    refreshRail();
    updateRail();

    /* ─────────────── Hero intro (GSAP timeline) ─────────────── */
    if (hasGSAP && !reduce) {
      const tl = window.gsap.timeline({ defaults: { ease: "expo.out" } });
      tl.from(".hero-greeting", { y: 24, autoAlpha: 0, duration: 0.9 })
        .from(".hero-brand", { y: 40, autoAlpha: 0, scale: 0.92, duration: 1.2 }, "-=0.4")
        .from(".hero-tag", { y: 20, autoAlpha: 0, duration: 0.8 }, "-=0.6")
        .from(".hero-meta > *", { y: 16, autoAlpha: 0, duration: 0.6, stagger: 0.05 }, "-=0.5")
        .from(".hero-char", { x: 60, y: 30, autoAlpha: 0, rotate: 10, duration: 1.0 }, "-=0.6")
        .from(".hero-char-left", { x: -60, autoAlpha: 0, rotate: -20, duration: 1.0 }, "<");
    }

    /* ─────────────── 마우스 parallax (hero 캐릭터) ─────────────── */
    const hero = $(".hero");
    const heroChar = $(".hero-char");
    const heroCharLeft = $(".hero-char-left");
    const heroBrand = $(".hero-brand");
    if (hero && !reduce) {
      let mx = 0, my = 0, raf = null;
      hero.addEventListener("mousemove", (e) => {
        const r = hero.getBoundingClientRect();
        mx = (e.clientX - r.left) / r.width - 0.5;
        my = (e.clientY - r.top) / r.height - 0.5;
        if (!raf) raf = requestAnimationFrame(applyParallax);
      });
      hero.addEventListener("mouseleave", () => {
        mx = 0; my = 0;
        if (!raf) raf = requestAnimationFrame(applyParallax);
      });
      function applyParallax() {
        raf = null;
        if (heroChar) heroChar.style.transform = `translate3d(${mx * -18}px, ${my * -12}px, 0) rotate(-4deg)`;
        if (heroCharLeft) heroCharLeft.style.transform = `translate3d(${mx * 14}px, ${my * 18}px, 0) rotate(-12deg)`;
        if (heroBrand) heroBrand.style.transform = `translate3d(${mx * -6}px, ${my * -4}px, 0)`;
      }
    }

    /* ─────────────── Journey — sticky stage + frame switch ─────────────── */
    const journey = $("#journey");
    const frames = $$(".journey .frame");
    const runner = $("#routeRunner");
    const runSprite = runner ? makeSprite(runner, 95) : null;

    function setActiveFrame(targetIdx) {
      frames.forEach((f, i) => f.classList.toggle("active", i === targetIdx));
    }

    if (journey && frames.length && hasST) {
      // 각 frame 에 대해 ScrollTrigger 를 만들어 scrubbed 로 진행
      const segCount = frames.length;
      const journeyH = () => journey.offsetHeight;
      const segLen = () => (journeyH() - innerHeight) / segCount;

      // 전체 ScrollTrigger — frame switching (가벼운 onUpdate)
      let cachedVisualH = 0;
      function refreshVisualH() {
        const v = $(".frame-route-visual");
        cachedVisualH = v ? v.offsetHeight : 0;
      }
      window.ScrollTrigger.create({
        trigger: journey,
        start: "top top",
        end: () => `+=${journeyH() - innerHeight}`,
        scrub: true,
        onRefresh: refreshVisualH,
        onUpdate: (self) => {
          const p = self.progress;
          let i = Math.floor(p * segCount);
          if (i >= segCount) i = segCount - 1;
          if (i < 0) i = 0;
          if (!frames[i].classList.contains("active")) {
            setActiveFrame(i);
            onFrameActivate(frames[i]);
          }
          // route sprite — route frame 안에 있을 때 항상 재생 보장
          if (runSprite) {
            if (i === 2 && !runSprite.on) runSprite.play();
            else if (i !== 2 && runSprite.on) runSprite.stop();
          }
          // route runner 위치 — transform 으로 GPU 가속
          if (i === 2 && runner && cachedVisualH > 0) {
            const local = clamp(p * segCount - 2, 0, 1);
            runner.style.transform = `translate(-50%, calc(-50% + ${local * cachedVisualH}px))`;
          }
        },
      });
      refreshVisualH();

      // 첫 frame 초기 활성
      setActiveFrame(0);

      // 각 frame 활성화 시 가벼운 reveal — GSAP 없이 CSS 전환 + 카운트업만
      const ranOnce = new WeakSet();
      function onFrameActivate(frame) {
        if (ranOnce.has(frame)) return;
        ranOnce.add(frame);
        // count up — frame 안의 [data-count]
        $$("[data-count]", frame).forEach((el) => {
          const to = parseFloat(el.dataset.count);
          const obj = { v: 0 };
          window.gsap.to(obj, {
            v: to,
            duration: 1.0,
            ease: "power3.out",
            onUpdate() { el.textContent = Math.round(obj.v); },
          });
        });
      }
      // 첫 frame 즉시
      if (frames[0]) onFrameActivate(frames[0]);
      // 이후 frame 들은 ScrollTrigger onUpdate 에서 처리

      // 첫 frame 은 즉시 reveal
      if (frames[0]) {
        frames[0].classList.add("active");
      }
    } else if (frames.length) {
      // GSAP 없으면 모든 frame 보이게
      frames.forEach((f) => f.classList.add("active"));
    }

    /* ─────────────── Closing + footer ─────────────── */
    if ("IntersectionObserver" in window) {
      const io = new IntersectionObserver((entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("in");
            io.unobserve(e.target);
          }
        });
      }, { threshold: 0.15 });
      $$(".closing, .foot").forEach((el) => io.observe(el));
    }

    /* ─────────────── Anchor scroll → frame 위치로 ─────────────── */
    $$('a[href^="#"]').forEach((a) => {
      a.addEventListener("click", (e) => {
        const id = a.getAttribute("href");
        if (id.length <= 1) return;
        const t = $(id);
        if (!t) return;
        e.preventDefault();
        const frame = t.closest(".frame");
        if (frame && journey) {
          const idx = parseInt(frame.dataset.frame, 10) || 0;
          const total = journey.offsetHeight - innerHeight;
          const per = total / frames.length;
          const target = journey.offsetTop + per * idx + per * 0.3;
          window.scrollTo({ top: target, behavior: "smooth" });
        } else {
          const top = t.getBoundingClientRect().top + window.scrollY - 70;
          window.scrollTo({ top, behavior: "smooth" });
        }
      });
    });

    /* ─────────────── 로드 안정화 ─────────────── */
    addEventListener("load", () => {
      updateRail();
      if (hasST) window.ScrollTrigger.refresh();
    });
  }

  // GSAP/Lenis 가 defer 라 load 후 init
  if (document.readyState === "complete") setup();
  else addEventListener("load", setup);
})();
