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

    /* ─────────────── 화면 크기 고정 (CSS zoom) ───────────────
     * 디자인 기준 1440 × 820. viewport 다르면 zoom 비율로 1:1 스케일.
     * zoom 은 transform 과 달리 sticky/containing block 안 깨뜨림.
     * too-small 진입/탈출 시 모든 캐시 + ScrollTrigger 강제 재계산. */
    const DESIGN_W = 1440;
    const DESIGN_H = 820;
    const MIN_W = 1280;
    const MIN_H = 720;
    const tooSmall = document.getElementById("too-small");
    let isTooSmall = false;
    function applyZoom() {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const small = w < MIN_W || h < MIN_H;
      if (small) {
        document.body.style.zoom = "";
        if (tooSmall) tooSmall.hidden = false;
        isTooSmall = true;
        return;
      }
      if (tooSmall) tooSmall.hidden = true;
      isTooSmall = false;
      const z = Math.min(w / DESIGN_W, h / DESIGN_H);
      document.body.style.zoom = String(z);
    }
    applyZoom();

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
    refreshRail();
    updateRail();

    /* ─────────────── Hero intro (GSAP timeline) ─────────────── */
    if (hasGSAP && !reduce) {
      const tl = window.gsap.timeline({ defaults: { ease: "expo.out" } });
      tl.from(".hero-greeting", { y: 24, autoAlpha: 0, duration: 0.9 })
        .from(".hero-brand", { y: 40, autoAlpha: 0, scale: 0.92, duration: 1.2 }, "-=0.4")
        .from(".hero-tag", { y: 20, autoAlpha: 0, duration: 0.8 }, "-=0.6")
        .from(".hero-meta > *", { y: 16, autoAlpha: 0, duration: 0.6, stagger: 0.05 }, "-=0.5");
      // hero-char 가 존재할 때만 (HTML 에서 doodle 로 대체되어 빠질 수 있음)
      if ($(".hero-char")) {
        tl.from(".hero-char", { x: 60, y: 30, autoAlpha: 0, rotate: 10, duration: 1.0 }, "-=0.6");
      }
    }

    /* ─────────────── 마우스 parallax (hero 캐릭터) ─────────────── */
    const hero = $(".hero");
    const heroChar = $(".hero-char");
    const heroBrand = $(".hero-brand");
    if (hero && !reduce && (heroChar || heroBrand)) {
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

      let cachedVisualH = 0;
      function refreshVisualH() {
        const v = $(".frame-route-visual");
        cachedVisualH = v ? v.offsetHeight : 0;
      }

      // ★ 반드시 ScrollTrigger.create 전에 선언 — 초기화 시 onUpdate 가 즉시 호출되므로 TDZ 에러 방지
      const ranOnce = new WeakSet();
      function onFrameActivate(frame) {
        if (ranOnce.has(frame)) return;
        ranOnce.add(frame);
        const countEls = $$("[data-count]", frame);
        if (!countEls.length || !hasGSAP) return;
        countEls.forEach((el) => {
          const to = parseFloat(el.dataset.count);
          if (isNaN(to)) return;
          const obj = { v: 0 };
          window.gsap.to(obj, {
            v: to,
            duration: 1.0,
            ease: "power3.out",
            onUpdate() { el.textContent = Math.round(obj.v); },
          });
        });
      }

      // 전체 ScrollTrigger — frame switching (ranOnce / onFrameActivate 가 위에 선언되어 있어 안전)
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
          if (runSprite) {
            if (i === 2 && !runSprite.on) runSprite.play();
            else if (i !== 2 && runSprite.on) runSprite.stop();
          }
          if (i === 2 && runner && cachedVisualH > 0) {
            const local = clamp(p * segCount - 2, 0, 1);
            runner.style.transform = `translate(-50%, calc(-50% + ${local * cachedVisualH}px))`;
          }
        },
      });
      refreshVisualH();

      // 첫 frame 초기 활성 + countup
      setActiveFrame(0);
      if (frames[0]) onFrameActivate(frames[0]);
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

    /* ─────────────── resize 핸들러 ───────────────
     * 일반 resize: zoom 만 재계산 + 캐시 갱신 + ScrollTrigger refresh
     * 단, **too-small <-> 큰 화면 경계 통과** 시: ScrollTrigger 가 zoom 전환의
     *   극단 상태를 깨끗이 못 따라가서 frame 활성 stuck 문제 발생. 가장 확실한 해법은 reload. */
    let resizeT = null;
    let lastTooSmall = isTooSmall;
    function onResize() {
      if (resizeT) clearTimeout(resizeT);
      resizeT = setTimeout(() => {
        const wasTooSmall = lastTooSmall;
        applyZoom();
        // too-small 경계를 통과한 경우 = 어떤 방향이든 reload 가 가장 안정적
        if (wasTooSmall !== isTooSmall) {
          lastTooSmall = isTooSmall;
          window.location.reload();
          return;
        }
        lastTooSmall = isTooSmall;
        // 일반 resize: 캐시 갱신만
        requestAnimationFrame(() => requestAnimationFrame(() => {
          refreshDocH();
          refreshRail();
          if (typeof refreshVisualH === "function") refreshVisualH();
          if (hasST) {
            window.ScrollTrigger.refresh();
            window.ScrollTrigger.update();
          }
          updateRail();
          updateProg();
        }));
      }, 150);
    }
    window.addEventListener("resize", onResize);

    /* ─────────────── 로드 안정화 ─────────────── */
    addEventListener("load", () => {
      onResize();
    });
  }

  // GSAP/Lenis 가 defer 라 load 후 init
  if (document.readyState === "complete") setup();
  else addEventListener("load", setup);
})();
