import React, {
  useState, useEffect, useRef, useCallback, useMemo,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { mockScheduleList, mockRouteData } from '../data/mockData';
import './CalendarV5B.css';

/* ── Constants ─────────────────────────────────────────────────── */

const WEEK = [
  { eng: 'MON', ko: '월', jsDay: 1 }, { eng: 'TUE', ko: '화', jsDay: 2 },
  { eng: 'WED', ko: '수', jsDay: 3 }, { eng: 'THU', ko: '목', jsDay: 4 },
  { eng: 'FRI', ko: '금', jsDay: 5 }, { eng: 'SAT', ko: '토', jsDay: 6 },
  { eng: 'SUN', ko: '일', jsDay: 0 },
];

const PLACE_COORDS = {
  '우이동':             { lat: 37.6596, lng: 127.0116 },
  '집':                { lat: 37.6596, lng: 127.0116 },
  '국민대학교':          { lat: 37.6094, lng: 126.9938 },
  '강남역 토익학원':     { lat: 37.4980, lng: 127.0276 },
  '쌍문동 피트니스센터': { lat: 37.6481, lng: 127.0283 },
  '혜화역 스터디카페':   { lat: 37.5822, lng: 127.0020 },
  '수유역 내과의원':     { lat: 37.6414, lng: 127.0246 },
  '홍대입구역 카페':     { lat: 37.5575, lng: 126.9237 },
  '수유역 편의점':       { lat: 37.6412, lng: 127.0245 },
};

/* First routine has full segment data */
const ROUTE_BY_ID = {
  [mockScheduleList.data[0]?.scheduleId]: mockRouteData.data.candidates[0],
};

const EMPTY_FORM = {
  title: '', originName: '', destinationName: '', arrivalTime: '09:00', daysOfWeek: [],
};

/* ── Helpers ───────────────────────────────────────────────────── */

function resolveCoords(place) {
  if (place.lat != null && place.lng != null) return { lat: place.lat, lng: place.lng };
  return PLACE_COORDS[place.name] ?? { lat: 37.5665, lng: 126.9780 };
}

function isoToHHMM(iso) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function calcDepTime(arrIso, durMin) {
  const dep = new Date(new Date(arrIso).getTime() - durMin * 60000);
  return `${String(dep.getHours()).padStart(2, '0')}:${String(dep.getMinutes()).padStart(2, '0')}`;
}

function calcCountdown(reminderAt) {
  const now = new Date();
  const src = new Date(reminderAt);
  const t = new Date(now.getFullYear(), now.getMonth(), now.getDate(),
    src.getHours(), src.getMinutes(), 0);
  const diff = t - now;
  return diff > 0 ? Math.ceil(diff / 60_000) : 0;
}

function formatCountdown(min) {
  if (min <= 0) return { num: '지금', unit: '출발' };
  if (min < 60) return { num: String(min), unit: '분 후' };
  const h = Math.floor(min / 60), m = min % 60;
  return m === 0 ? { num: `${h}시간`, unit: '후' } : { num: `${h}시간 ${m}분`, unit: '후' };
}

function shortenName(n = '') {
  return n.replace('대학교', '대').replace('입구역', '역').replace('정류장', '').slice(0, 7);
}

function buildRoutines(raw) {
  return raw
    .filter(s => s.routineRule?.daysOfWeek?.length > 0)
    .map(s => ({
      ...s,
      _orig: resolveCoords(s.origin),
      _dest: resolveCoords(s.destination),
    }));
}

const INITIAL_ROUTINES = buildRoutines(mockScheduleList.data);

/* ── Map animation helpers ─────────────────────────────────────── */

function densifyPath(pathCoords, n = 60) {
  if (pathCoords.length < 2) return pathCoords;
  const out = [];
  for (let i = 0; i < pathCoords.length - 1; i++) {
    const [lng1, lat1] = pathCoords[i];
    const [lng2, lat2] = pathCoords[i + 1];
    for (let j = 0; j < n; j++) {
      const t = j / n;
      out.push([lng1 + (lng2 - lng1) * t, lat1 + (lat2 - lat1) * t]);
    }
  }
  out.push(pathCoords[pathCoords.length - 1]);
  return out;
}

function easeInOut(t) {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

function arcPath(from, to, n = 60) {
  const dlat = to.lat - from.lat, dlng = to.lng - from.lng;
  const len  = Math.sqrt(dlat * dlat + dlng * dlng) || 1;
  const scale = Math.min(len * 0.4, 0.06);
  const midLat = (from.lat + to.lat) / 2 + (-dlng / len) * scale;
  const midLng = (from.lng + to.lng) / 2 + ( dlat / len) * scale;
  const pts = [];
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    pts.push({
      lat: (1-t)*(1-t)*from.lat + 2*(1-t)*t*midLat + t*t*to.lat,
      lng: (1-t)*(1-t)*from.lng + 2*(1-t)*t*midLng + t*t*to.lng,
    });
  }
  return pts;
}

/* ── Marker HTML builders ──────────────────────────────────────── */

function markerStartHtml(name) {
  return `<div style="display:flex;align-items:center;gap:6px;background:rgba(255,255,255,0.92);backdrop-filter:blur(8px);padding:6px 14px 6px 8px;border-radius:24px;box-shadow:0 2px 12px rgba(0,0,0,0.15);font-family:'Noto Sans KR',sans-serif;white-space:nowrap;">
    <div style="width:10px;height:10px;background:#1a1a1a;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:12px;font-weight:600;color:#1a1a1a;">${name}</span>
  </div>`;
}

function markerEndHtml(name) {
  return `<div style="display:flex;align-items:center;gap:6px;background:#3B82F6;padding:6px 14px 6px 8px;border-radius:24px;box-shadow:0 2px 12px rgba(59,130,246,0.3);font-family:'Noto Sans KR',sans-serif;white-space:nowrap;">
    <div style="width:10px;height:10px;background:white;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:12px;font-weight:600;color:white;">${shortenName(name)}</span>
  </div>`;
}

function markerTransferHtml(name) {
  return `<div style="display:flex;align-items:center;gap:4px;background:rgba(255,255,255,0.92);backdrop-filter:blur(6px);padding:4px 10px 4px 6px;border-radius:20px;box-shadow:0 1px 6px rgba(0,0,0,0.1);font-family:'Noto Sans KR',sans-serif;white-space:nowrap;">
    <div style="width:6px;height:6px;background:#888;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:10px;font-weight:500;color:#888;">${shortenName(name)}</span>
  </div>`;
}

/* ================================================================
   MapView
   ================================================================ */

function MapView({ routine }) {
  const mapDivRef    = useRef(null);
  const kakaoMapRef  = useRef(null);
  const polylinesRef = useRef([]);
  const overlaysRef  = useRef([]);
  const rafRef       = useRef(null);
  const [mapReady, setMapReady]   = useState(false);
  const [loadError, setLoadError] = useState(false);

  /* ── Init once ── */
  useEffect(() => {
    let cancelled = false;
    function init() {
      const kakao = window.kakao;
      if (!kakao?.maps) { setLoadError(true); return; }
      kakao.maps.load(() => {
        if (cancelled || !mapDivRef.current) return;
        kakaoMapRef.current = new kakao.maps.Map(mapDivRef.current, {
          center: new kakao.maps.LatLng(37.58, 127.00),
          level: 7,
        });
        setMapReady(true);
      });
    }
    if (window.kakao?.maps) { init(); }
    else {
      const timer = setInterval(() => {
        if (window.kakao?.maps) { clearInterval(timer); clearTimeout(tOut); init(); }
      }, 100);
      const tOut = setTimeout(() => { clearInterval(timer); if (!cancelled) setLoadError(true); }, 5000);
      return () => { cancelled = true; clearInterval(timer); clearTimeout(tOut); };
    }
    return () => { cancelled = true; };
  }, []); // eslint-disable-line

  /* ── Redraw when routine changes ── */
  useEffect(() => {
    const map = kakaoMapRef.current;
    if (!map || !mapReady || !routine) return;
    const kakao = window.kakao;

    if (rafRef.current) { cancelAnimationFrame(rafRef.current); rafRef.current = null; }
    polylinesRef.current.forEach(p => p.setMap(null));
    overlaysRef.current.forEach(o => o.setMap(null));
    polylinesRef.current = [];
    overlaysRef.current = [];

    const addOverlay = (opts) => {
      const o = new kakao.maps.CustomOverlay(opts);
      overlaysRef.current.push(o);
      return o;
    };

    const cand = ROUTE_BY_ID[routine.scheduleId];

    if (cand?.segments?.length > 0) {
      /* ── Full segmented route (animated) ── */
      const { segments } = cand;
      const densePaths = segments.map(seg => densifyPath(seg.path ?? [], 60));

      const bounds = new kakao.maps.LatLngBounds();
      densePaths.forEach(p => p.forEach(([lng, lat]) => bounds.extend(new kakao.maps.LatLng(lat, lng))));
      map.setBounds(bounds, 80, 300, 24, 24);

      // Start marker
      const fp = densePaths[0][0];
      addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
        position: new kakao.maps.LatLng(fp[1], fp[0]),
        content: markerStartHtml(segments[0].from),
      });
      // End marker
      const lp = densePaths[densePaths.length - 1];
      const ep = lp[lp.length - 1];
      addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
        position: new kakao.maps.LatLng(ep[1], ep[0]),
        content: markerEndHtml(segments[segments.length - 1].to),
      });
      // Transfer markers
      for (let i = 0; i < segments.length - 1; i++) {
        const curr = segments[i], next = segments[i + 1];
        if (curr.mode === next.mode) continue;
        if ((i + 1 === segments.length - 1) && next.mode === 'WALK') continue;
        const [lng, lat] = densePaths[i + 1][0];
        addOverlay({ map, zIndex: 5, yAnchor: 0.5, xAnchor: 0.5,
          position: new kakao.maps.LatLng(lat, lng),
          content: markerTransferHtml(next.from ?? ''),
        });
      }

      // Polylines
      const polylines = segments.map(seg =>
        new kakao.maps.Polyline({
          map, path: [],
          strokeWeight: seg.mode === 'WALK' ? 3 : 4,
          strokeColor: '#3B82F6', strokeOpacity: 0.8,
          strokeStyle: seg.mode === 'WALK' ? 'shortdash' : 'solid',
        })
      );
      polylinesRef.current = polylines;

      // Moving dot
      const dotDiv = document.createElement('div');
      dotDiv.style.cssText = 'width:12px;height:12px;background:#3B82F6;border-radius:50%;border:2px solid white;box-shadow:0 0 8px rgba(59,130,246,0.4);transform:translate(-50%,-50%);';
      const dotOv = addOverlay({ content: dotDiv, zIndex: 15 });

      const allDense = densePaths.flat();
      const total = allDense.length;
      const DRAW = 1500, MOVE = 4000;
      let t0 = null, phase = 'drawing', lc = false;

      function animate(ts) {
        if (lc) return;
        if (phase === 'drawing') {
          if (!t0) t0 = ts;
          const raw = Math.min((ts - t0) / DRAW, 1);
          const target = Math.floor(easeInOut(raw) * total);
          let drawn = 0;
          for (let si = 0; si < densePaths.length; si++) {
            if (drawn >= target) break;
            const take = Math.min(target - drawn, densePaths[si].length);
            polylines[si].setPath(
              densePaths[si].slice(0, take).map(([lng, lat]) => new kakao.maps.LatLng(lat, lng))
            );
            drawn += take;
          }
          rafRef.current = raw < 1
            ? requestAnimationFrame(animate)
            : (phase = 'moving', t0 = null, dotOv.setMap(map), requestAnimationFrame(animate));
        } else {
          if (!t0) t0 = ts;
          const idx = Math.min(Math.floor(((ts - t0) % MOVE) / MOVE * total), total - 1);
          const [lng, lat] = allDense[idx];
          dotOv.setPosition(new kakao.maps.LatLng(lat, lng));
          rafRef.current = requestAnimationFrame(animate);
        }
      }
      rafRef.current = requestAnimationFrame(animate);
      return () => { lc = true; };

    } else {
      /* ── Simple arc route ── */
      const { _orig, _dest } = routine;
      const arc = arcPath(_orig, _dest);

      const bounds = new kakao.maps.LatLngBounds();
      arc.forEach(p => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)));
      map.setBounds(bounds, 80, 300, 24, 24);

      addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
        position: new kakao.maps.LatLng(_orig.lat, _orig.lng),
        content: markerStartHtml(routine.origin.name),
      });
      addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
        position: new kakao.maps.LatLng(_dest.lat, _dest.lng),
        content: markerEndHtml(routine.destination.name),
      });

      // Animated arc draw
      const polyline = new kakao.maps.Polyline({
        map, path: [],
        strokeColor: '#3B82F6', strokeWeight: 4,
        strokeOpacity: 0.8, strokeStyle: 'solid',
      });
      polylinesRef.current = [polyline];

      const DRAW = 1000;
      let t0 = null, lc = false;
      function animateArc(ts) {
        if (lc) return;
        if (!t0) t0 = ts;
        const raw = Math.min((ts - t0) / DRAW, 1);
        const target = Math.floor(easeInOut(raw) * arc.length);
        polyline.setPath(arc.slice(0, target).map(p => new kakao.maps.LatLng(p.lat, p.lng)));
        if (raw < 1) rafRef.current = requestAnimationFrame(animateArc);
      }
      rafRef.current = requestAnimationFrame(animateArc);
      return () => { lc = true; };
    }
  }, [routine?.scheduleId, mapReady]); // eslint-disable-line

  return (
    <div className="cv5b-map">
      <div ref={mapDivRef} style={{ width: '100%', height: '100%' }} />
      {!mapReady && !loadError && (
        <div className="cv5b-map__loading">
          <div className="cv5b-spinner" />
          <span>지도 불러오는 중…</span>
        </div>
      )}
      {loadError && (
        <div className="cv5b-map__loading">
          <span>지도를 불러올 수 없어요</span>
        </div>
      )}
    </div>
  );
}

/* ================================================================
   RoutineCard
   ================================================================ */

function RoutineCard({ routine, todayDow, onEdit, onDelete }) {
  const active  = routine.routineRule?.daysOfWeek ?? [];
  const arrTime = isoToHHMM(routine.arrivalTime);
  const depTime = calcDepTime(routine.arrivalTime, routine.averageDurationMinutes);

  const [countdown, setCountdown] = useState(() => calcCountdown(routine.reminderAt));
  useEffect(() => {
    const id = setInterval(() => setCountdown(calcCountdown(routine.reminderAt)), 60_000);
    return () => clearInterval(id);
  }, [routine.reminderAt]);

  const { num: cdNum, unit: cdUnit } = formatCountdown(countdown);

  const cand      = ROUTE_BY_ID[routine.scheduleId];
  const duration  = cand?.totalDurationMinutes  ?? routine.averageDurationMinutes;
  const transfers = cand?.totalTransfers         ?? 1;
  const totalWalk = cand?.segments
    ? cand.segments.filter(s => s.mode === 'WALK').reduce((sum, s) => sum + (s.distanceMeters ?? 0), 0)
    : 350;

  return (
    <div className="cv5b-card">

      {/* ── Header ── */}
      <div className="cv5b-card__head">
        <div className="cv5b-card__head-left">
          <p className="cv5b-card__title">{routine.title}</p>
          <p className="cv5b-card__route">{routine.origin.name} → {routine.destination.name}</p>
        </div>
        <span className="cv5b-card__arr">{arrTime}</span>
      </div>

      {/* ── Days of week ── */}
      <div className="cv5b-card__days">
        {WEEK.map(({ eng, ko, jsDay }) => {
          const isOn    = active.includes(eng);
          const isToday = jsDay === todayDow;
          return (
            <div
              key={eng}
              className={[
                'cv5b-card__day',
                isOn && isToday ? 'cv5b-card__day--today-on' :
                isOn            ? 'cv5b-card__day--on'       :
                                  'cv5b-card__day--off',
              ].join(' ')}
            >
              {ko}
            </div>
          );
        })}
      </div>

      {/* ── Stats ── */}
      <div className="cv5b-card__stats">
        <div className="cv5b-card__stat">
          <span className="cv5b-card__stat-num">{duration}</span>
          <span className="cv5b-card__stat-label">분 소요</span>
        </div>
        <div className="cv5b-card__stat-sep" />
        <div className="cv5b-card__stat">
          <span className="cv5b-card__stat-num">{transfers}</span>
          <span className="cv5b-card__stat-label">회 환승</span>
        </div>
        <div className="cv5b-card__stat-sep" />
        <div className="cv5b-card__stat">
          <span className="cv5b-card__stat-num">{totalWalk}</span>
          <span className="cv5b-card__stat-label">m 도보</span>
        </div>
      </div>

      {/* ── Depart + Countdown ── */}
      <div className="cv5b-card__depart">
        <div>
          <p className="cv5b-card__dep-main">{depTime} 출발 → {arrTime} 도착</p>
          <p className="cv5b-card__dep-sub">여유 30분 포함</p>
        </div>
        <div className="cv5b-card__countdown">
          <span className="cv5b-card__cd-num">{cdNum}</span>
          <span className="cv5b-card__cd-unit">{cdUnit}</span>
        </div>
      </div>

      {/* ── Actions ── */}
      <div className="cv5b-card__actions">
        <button className="cv5b-card__btn cv5b-card__btn--route">경로 보기</button>
        <button className="cv5b-card__btn cv5b-card__btn--edit"  onClick={() => onEdit(routine)}>수정</button>
        <button className="cv5b-card__btn cv5b-card__btn--del"   onClick={() => onDelete(routine)}>삭제</button>
      </div>

    </div>
  );
}

/* ── Add card ──────────────────────────────────────────────────── */
function AddCard({ onClick }) {
  return (
    <div className="cv5b-add-card" onClick={onClick}>
      <span className="cv5b-add-card__plus">+</span>
      <span className="cv5b-add-card__label">새 루틴 추가</span>
    </div>
  );
}

/* ── Add / Edit Bottom Sheet ───────────────────────────────────── */
function RoutineSheet({ mode, init, onSave, onCancel }) {
  const [form, setForm] = useState(init ?? EMPTY_FORM);
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));
  const toggleDay = eng => setForm(f => ({
    ...f,
    daysOfWeek: f.daysOfWeek.includes(eng)
      ? f.daysOfWeek.filter(d => d !== eng)
      : [...f.daysOfWeek, eng],
  }));

  return (
    <div className="cv5b-overlay" onClick={onCancel}>
      <div className="cv5b-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv5b-sheet__handle" />
        <p className="cv5b-sheet__heading">{mode === 'add' ? '새 루틴 추가' : '루틴 수정'}</p>

        <input className="cv5b-sheet__input" placeholder="루틴 이름"
          value={form.title} onChange={e => set('title', e.target.value)} />

        <div className="cv5b-sheet__hint-wrap">
          <input className="cv5b-sheet__input" placeholder="출발지 검색"
            value={form.originName} onChange={e => set('originName', e.target.value)} />
          <span className="cv5b-sheet__hint">기본: 우이동</span>
        </div>

        <input className="cv5b-sheet__input" placeholder="도착지 검색"
          value={form.destinationName} onChange={e => set('destinationName', e.target.value)} />

        <div className="cv5b-sheet__hint-wrap">
          <input className="cv5b-sheet__input cv5b-sheet__input--time" type="time"
            value={form.arrivalTime} onChange={e => set('arrivalTime', e.target.value)} />
          <span className="cv5b-sheet__hint">도착 희망 시간</span>
        </div>

        <p className="cv5b-sheet__days-label">반복 요일</p>
        <div className="cv5b-sheet__days">
          {WEEK.map(({ eng, ko }) => (
            <button
              key={eng}
              className={`cv5b-sheet__day${form.daysOfWeek.includes(eng) ? ' cv5b-sheet__day--on' : ''}`}
              onClick={() => toggleDay(eng)}
            >
              {ko}
            </button>
          ))}
        </div>

        <div className="cv5b-sheet__footer">
          <button className="cv5b-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv5b-sheet__save"   onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── Delete dialog ─────────────────────────────────────────────── */
function DeleteDialog({ routine, onConfirm, onCancel }) {
  return (
    <div className="cv5b-overlay cv5b-overlay--center" onClick={onCancel}>
      <div className="cv5b-dialog" onClick={e => e.stopPropagation()}>
        <p className="cv5b-dialog__msg">이 루틴을 삭제할까요?</p>
        <p className="cv5b-dialog__name">{routine.title}</p>
        <div className="cv5b-dialog__btns">
          <button className="cv5b-dialog__cancel" onClick={onCancel}>취소</button>
          <button className="cv5b-dialog__del"    onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   CalendarV5B — Main
   ================================================================ */

export default function CalendarV5B() {
  const navigate = useNavigate();
  const todayDow = new Date().getDay();

  const [routines,     setRoutines]     = useState(INITIAL_ROUTINES);
  const [activeIdx,    setActiveIdx]    = useState(0);
  const [sheetState,   setSheetState]   = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  const scrollRef = useRef(null);

  /* ── Active card detection from scroll position ── */
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    function getActiveIdx() {
      const center = el.scrollLeft + el.clientWidth / 2;
      let best = 0, bestDist = Infinity;
      Array.from(el.children).forEach((child, i) => {
        const dist = Math.abs(child.offsetLeft + child.offsetWidth / 2 - center);
        if (dist < bestDist) { bestDist = dist; best = i; }
      });
      return best;
    }

    let timer;
    const onScroll = () => {
      clearTimeout(timer);
      timer = setTimeout(() => setActiveIdx(getActiveIdx()), 60);
    };

    el.addEventListener('scroll', onScroll, { passive: true });
    return () => { el.removeEventListener('scroll', onScroll); clearTimeout(timer); };
  }, []); // eslint-disable-line

  /* ── Scroll to card by index ── */
  const scrollToIdx = useCallback((idx) => {
    const el = scrollRef.current;
    if (!el) return;
    const child = el.children[idx];
    if (!child) return;
    child.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  }, []);

  /* Active routine for map (clamp to routines range — add card has no route) */
  const activeRoutine = useMemo(
    () => routines[Math.min(activeIdx, routines.length - 1)] ?? null,
    [routines, activeIdx],
  );

  /* ── Dot indicator count (routines + add card) ── */
  const totalCards = routines.length + 1;

  /* ── Save ── */
  const handleSave = (form) => {
    if (sheetState.mode === 'add') {
      const [h, m] = form.arrivalTime.split(':');
      const base = new Date(); base.setHours(+h, +m, 0, 0);
      const origC = PLACE_COORDS[form.originName] ?? { lat: 37.6596, lng: 127.0116 };
      const destC = PLACE_COORDS[form.destinationName] ?? { lat: 37.5665, lng: 126.9780 };
      const next = {
        scheduleId: `sch_${Date.now()}`,
        title: form.title || '새 루틴',
        origin:      { name: form.originName      || '출발지', ...origC },
        destination: { name: form.destinationName || '도착지', ...destC },
        arrivalTime: base.toISOString(), reminderAt: base.toISOString(),
        averageDurationMinutes: 30,
        routineRule: { type: 'WEEKLY', daysOfWeek: form.daysOfWeek.length > 0 ? form.daysOfWeek : ['MON'] },
        _orig: origC, _dest: destC,
      };
      setRoutines(prev => [...prev, next]);
      setTimeout(() => scrollToIdx(routines.length), 120);
    } else {
      setRoutines(prev => prev.map(r => {
        if (r.scheduleId !== sheetState.routineId) return r;
        const [h, m] = form.arrivalTime.split(':');
        const arr = new Date(r.arrivalTime); arr.setHours(+h, +m, 0, 0);
        return {
          ...r,
          title:       form.title       || r.title,
          origin:      { ...r.origin,      name: form.originName      || r.origin.name },
          destination: { ...r.destination, name: form.destinationName || r.destination.name },
          arrivalTime: arr.toISOString(),
          routineRule: { type: 'WEEKLY', daysOfWeek: form.daysOfWeek.length > 0 ? form.daysOfWeek : (r.routineRule?.daysOfWeek ?? ['MON']) },
        };
      }));
    }
    setSheetState(null);
  };

  /* ── Delete ── */
  const handleDeleteConfirm = () => {
    const id = deleteTarget.scheduleId;
    setRoutines(prev => prev.filter(r => r.scheduleId !== id));
    setActiveIdx(prev => Math.max(0, prev - 1));
    setDeleteTarget(null);
  };

  const openAdd = () => setSheetState({ mode: 'add', init: { ...EMPTY_FORM } });

  return (
    <div className="cv5b">

      {/* ── Map ── */}
      <MapView routine={activeRoutine} />

      {/* ── Top bar ── */}
      <header className="cv5b-topbar">
        <button className="cv5b-topbar__back" onClick={() => navigate('/v5')}>←</button>
        <span className="cv5b-topbar__title">루틴</span>
        <span className="cv5b-topbar__count">{routines.length}개</span>
      </header>

      {/* ── Bottom: dots + cards ── */}
      <div className="cv5b-bottom">

        {/* Dot indicators */}
        <div className="cv5b-dots">
          {Array.from({ length: totalCards }).map((_, i) => (
            <button
              key={i}
              className={`cv5b-dot${i === activeIdx ? ' cv5b-dot--on' : ''}`}
              onClick={() => scrollToIdx(i)}
            />
          ))}
        </div>

        {/* Horizontal scroll cards */}
        <div className="cv5b-scroll" ref={scrollRef}>
          {routines.map(routine => (
            <RoutineCard
              key={routine.scheduleId}
              routine={routine}
              todayDow={todayDow}
              onEdit={r => setSheetState({
                mode: 'edit', routineId: r.scheduleId,
                init: {
                  title:           r.title,
                  originName:      r.origin.name,
                  destinationName: r.destination.name,
                  arrivalTime:     isoToHHMM(r.arrivalTime),
                  daysOfWeek:      r.routineRule?.daysOfWeek ?? [],
                },
              })}
              onDelete={r => setDeleteTarget(r)}
            />
          ))}
          <AddCard onClick={openAdd} />
        </div>

      </div>

      {/* ── FAB ── */}
      {!sheetState && !deleteTarget && (
        <button className="cv5b-fab" onClick={openAdd} title="루틴 추가">+</button>
      )}

      {/* ── Sheet ── */}
      {sheetState && (
        <RoutineSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={handleSave}
          onCancel={() => setSheetState(null)}
        />
      )}

      {/* ── Delete dialog ── */}
      {deleteTarget && (
        <DeleteDialog
          routine={deleteTarget}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

    </div>
  );
}
