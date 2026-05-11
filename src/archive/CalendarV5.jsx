import React, {
  useState, useEffect, useRef, useCallback, useMemo,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { mockScheduleList, mockRouteData } from '../data/mockData';
import './CalendarV5.css';

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

/* ── Arc path (quadratic bezier) ──────────────────────────────── */

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

/* ================================================================
   RoutineMap  — V5A 핀 방식, 팝업 없음
   activeId 변경 → 핀/아크 active/inactive 스타일 전환
   핀 클릭 → onPinClick(id) → 부모에서 카드 스크롤 처리
   ================================================================ */

function RoutineMap({ routines, activeId, onPinClick }) {
  const containerRef = useRef(null);
  const kakaoMapRef  = useRef(null);
  const overlaysRef  = useRef([]);
  const polylinesRef = useRef([]);
  const [mapReady, setMapReady]   = useState(false);
  const [loadError, setLoadError] = useState(false);

  /* ── Init (once) ── */
  useEffect(() => {
    let cancelled = false;
    function init() {
      const kakao = window.kakao;
      if (!kakao?.maps) { setLoadError(true); return; }
      kakao.maps.load(() => {
        if (cancelled || !containerRef.current) return;
        kakaoMapRef.current = new kakao.maps.Map(containerRef.current, {
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

  /* ── 핀 + 아크 전체 재드로우 (routines / activeId 변경 시) ── */
  useEffect(() => {
    const map = kakaoMapRef.current;
    if (!map || !mapReady || routines.length === 0) return;
    const kakao = window.kakao;

    overlaysRef.current.forEach(o => o.setMap(null));
    polylinesRef.current.forEach(p => p.setMap(null));
    overlaysRef.current = [];
    polylinesRef.current = [];

    const bounds = new kakao.maps.LatLngBounds();
    const drawnOrigins = new Set();

    routines.forEach((r, idx) => {
      const isActive = r.scheduleId === activeId;
      bounds.extend(new kakao.maps.LatLng(r._orig.lat, r._orig.lng));
      bounds.extend(new kakao.maps.LatLng(r._dest.lat, r._dest.lng));

      /* 출발지 핀 — 좌표 기준 중복 제거 */
      const origKey = `${r._orig.lat},${r._orig.lng}`;
      if (!drawnOrigins.has(origKey)) {
        drawnOrigins.add(origKey);
        const el = document.createElement('div');
        el.style.cssText = 'cursor:default;pointer-events:none;';
        el.innerHTML = `<div style="display:flex;align-items:center;gap:6px;background:rgba(255,255,255,0.92);backdrop-filter:blur(8px);padding:5px 12px 5px 8px;border-radius:24px;box-shadow:0 2px 12px rgba(0,0,0,0.12);font-family:'Noto Sans KR',sans-serif;white-space:nowrap;">
          <div style="width:8px;height:8px;background:#1a1a1a;border-radius:50%;flex-shrink:0;"></div>
          <span style="font-size:11px;font-weight:600;color:#1a1a1a;">${r.origin.name}</span>
        </div>`;
        overlaysRef.current.push(new kakao.maps.CustomOverlay({
          map, content: el, zIndex: 5,
          position: new kakao.maps.LatLng(r._orig.lat, r._orig.lng),
          yAnchor: 0.5, xAnchor: 0.5,
        }));
      }

      /* 도착지 핀 — 클릭 가능, active/inactive 스타일 */
      const destEl = document.createElement('div');
      destEl.style.cssText = 'cursor:pointer;';
      destEl.innerHTML = `<div style="display:flex;align-items:center;gap:6px;background:${isActive ? '#3B82F6' : 'rgba(255,255,255,0.88)'};backdrop-filter:blur(8px);padding:5px 12px 5px 8px;border-radius:24px;box-shadow:0 2px 12px ${isActive ? 'rgba(59,130,246,0.35)' : 'rgba(0,0,0,0.08)'};font-family:'Noto Sans KR',sans-serif;white-space:nowrap;animation:cv5-pin-drop 0.35s cubic-bezier(0.34,1.56,0.64,1) both;animation-delay:${idx * 0.1}s;">
        <div style="width:8px;height:8px;background:${isActive ? 'white' : '#aaa'};border-radius:50%;flex-shrink:0;"></div>
        <span style="font-size:11px;font-weight:600;color:${isActive ? 'white' : '#666'};">${shortenName(r.destination.name)}</span>
      </div>`;
      destEl.addEventListener('click', (e) => {
        e.stopPropagation();
        onPinClick(r.scheduleId);
      });
      overlaysRef.current.push(new kakao.maps.CustomOverlay({
        map, content: destEl, zIndex: 10,
        position: new kakao.maps.LatLng(r._dest.lat, r._dest.lng),
        yAnchor: 0.5, xAnchor: 0.5,
      }));

      /* 아크 경로선 */
      const arc = arcPath(r._orig, r._dest);
      polylinesRef.current.push(new kakao.maps.Polyline({
        map,
        path:          arc.map(p => new kakao.maps.LatLng(p.lat, p.lng)),
        strokeColor:   '#3B82F6',
        strokeWeight:  isActive ? 4 : 2,
        strokeOpacity: isActive ? 0.8 : 0.18,
        strokeStyle:   'solid',
      }));
    });

    /* 최초 1회만 fitBounds — 이후 activeId 변경은 핀/아크 스타일만 갱신 */
    if (overlaysRef.current.length > 0 && !kakaoMapRef.current.__fitted) {
      map.setBounds(bounds, 80, 320, 24, 24);
      kakaoMapRef.current.__fitted = true;
    }
  }, [routines, activeId, mapReady]); // eslint-disable-line

  return (
    <div className="cv5-map" ref={containerRef}>
      {!mapReady && !loadError && (
        <div className="cv5-map__loading">
          <div className="cv5-spinner" />
          <span>지도 불러오는 중…</span>
        </div>
      )}
      {loadError && (
        <div className="cv5-map__loading">
          <span>지도를 불러올 수 없어요</span>
        </div>
      )}
    </div>
  );
}

/* ================================================================
   RoutineCard  — V5B 카드 스타일
   ================================================================ */

function RoutineCard({ routine, todayDow, isActive, onEdit, onDelete }) {
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
    <div className={`cv5-card${isActive ? ' cv5-card--active' : ''}`}>

      {/* Header */}
      <div className="cv5-card__head">
        <div className="cv5-card__head-left">
          <p className="cv5-card__title">{routine.title}</p>
          <p className="cv5-card__route">{routine.origin.name} → {routine.destination.name}</p>
        </div>
        <span className="cv5-card__arr">{arrTime}</span>
      </div>

      {/* Days */}
      <div className="cv5-card__days">
        {WEEK.map(({ eng, ko, jsDay }) => {
          const isOn    = active.includes(eng);
          const isToday = jsDay === todayDow;
          return (
            <div
              key={eng}
              className={[
                'cv5-card__day',
                isOn && isToday ? 'cv5-card__day--today-on' :
                isOn            ? 'cv5-card__day--on'       :
                                  'cv5-card__day--off',
              ].join(' ')}
            >
              {ko}
            </div>
          );
        })}
      </div>

      {/* Stats */}
      <div className="cv5-card__stats">
        <div className="cv5-card__stat">
          <span className="cv5-card__stat-num">{duration}</span>
          <span className="cv5-card__stat-label">분 소요</span>
        </div>
        <div className="cv5-card__stat-sep" />
        <div className="cv5-card__stat">
          <span className="cv5-card__stat-num">{transfers}</span>
          <span className="cv5-card__stat-label">회 환승</span>
        </div>
        <div className="cv5-card__stat-sep" />
        <div className="cv5-card__stat">
          <span className="cv5-card__stat-num">{totalWalk}</span>
          <span className="cv5-card__stat-label">m 도보</span>
        </div>
      </div>

      {/* Depart + Countdown */}
      <div className="cv5-card__depart">
        <div>
          <p className="cv5-card__dep-main">{depTime} 출발 → {arrTime} 도착</p>
          <p className="cv5-card__dep-sub">여유 30분 포함</p>
        </div>
        <div className="cv5-card__countdown">
          <span className="cv5-card__cd-num">{cdNum}</span>
          <span className="cv5-card__cd-unit">{cdUnit}</span>
        </div>
      </div>

      {/* Actions */}
      <div className="cv5-card__actions">
        <button className="cv5-card__btn cv5-card__btn--route">경로 보기</button>
        <button className="cv5-card__btn cv5-card__btn--edit"  onClick={() => onEdit(routine)}>수정</button>
        <button className="cv5-card__btn cv5-card__btn--del"   onClick={() => onDelete(routine)}>삭제</button>
      </div>

    </div>
  );
}

/* ── Add card ──────────────────────────────────────────────────── */
function AddCard({ onClick }) {
  return (
    <div className="cv5-add-card" onClick={onClick}>
      <span className="cv5-add-card__plus">+</span>
      <span className="cv5-add-card__label">새 루틴 추가</span>
    </div>
  );
}

/* ── Bottom Sheet ──────────────────────────────────────────────── */
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
    <div className="cv5-overlay" onClick={onCancel}>
      <div className="cv5-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv5-sheet__handle" />
        <p className="cv5-sheet__heading">{mode === 'add' ? '새 루틴 추가' : '루틴 수정'}</p>

        <input className="cv5-sheet__input" placeholder="루틴 이름"
          value={form.title} onChange={e => set('title', e.target.value)} />

        <div className="cv5-sheet__hint-wrap">
          <input className="cv5-sheet__input" placeholder="출발지 검색"
            value={form.originName} onChange={e => set('originName', e.target.value)} />
          <span className="cv5-sheet__hint">기본: 우이동</span>
        </div>

        <input className="cv5-sheet__input" placeholder="도착지 검색"
          value={form.destinationName} onChange={e => set('destinationName', e.target.value)} />

        <div className="cv5-sheet__hint-wrap">
          <input className="cv5-sheet__input cv5-sheet__input--time" type="time"
            value={form.arrivalTime} onChange={e => set('arrivalTime', e.target.value)} />
          <span className="cv5-sheet__hint">도착 희망 시간</span>
        </div>

        <p className="cv5-sheet__days-label">반복 요일</p>
        <div className="cv5-sheet__days">
          {WEEK.map(({ eng, ko }) => (
            <button
              key={eng}
              className={`cv5-sheet__day${form.daysOfWeek.includes(eng) ? ' cv5-sheet__day--on' : ''}`}
              onClick={() => toggleDay(eng)}
            >
              {ko}
            </button>
          ))}
        </div>

        <div className="cv5-sheet__footer">
          <button className="cv5-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv5-sheet__save"   onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── Delete Dialog ─────────────────────────────────────────────── */
function DeleteDialog({ routine, onConfirm, onCancel }) {
  return (
    <div className="cv5-overlay cv5-overlay--center" onClick={onCancel}>
      <div className="cv5-dialog" onClick={e => e.stopPropagation()}>
        <p className="cv5-dialog__msg">이 루틴을 삭제할까요?</p>
        <p className="cv5-dialog__name">{routine.title}</p>
        <div className="cv5-dialog__btns">
          <button className="cv5-dialog__cancel" onClick={onCancel}>취소</button>
          <button className="cv5-dialog__del"    onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   CalendarV5  — 메인
   ================================================================ */

export default function CalendarV5() {
  const navigate = useNavigate();
  const todayDow = new Date().getDay();

  const [routines,     setRoutines]     = useState(INITIAL_ROUTINES);
  const [activeId,     setActiveId]     = useState(() => INITIAL_ROUTINES[0]?.scheduleId ?? null);
  const [scrollIdx,    setScrollIdx]    = useState(0);   // 점 인디케이터용 (add 카드 포함)
  const [sheetState,   setSheetState]   = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  const scrollRef = useRef(null);

  /* ── 스크롤 감지 → activeId + scrollIdx 갱신 ── */
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    function getIdx() {
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
      timer = setTimeout(() => {
        const idx = getIdx();
        setScrollIdx(idx);
        const routine = routines[idx];
        if (routine) setActiveId(routine.scheduleId); // add 카드면 변경 안 함
      }, 60);
    };

    el.addEventListener('scroll', onScroll, { passive: true });
    return () => { el.removeEventListener('scroll', onScroll); clearTimeout(timer); };
  }, [routines]);

  /* ── 카드로 스크롤 ── */
  const scrollToIdx = useCallback((idx) => {
    const el = scrollRef.current;
    if (!el) return;
    const child = el.children[idx];
    if (child) child.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  }, []);

  /* ── 핀 클릭 → activeId 변경 + 카드 자동 스크롤 ── */
  const handlePinClick = useCallback((id) => {
    setActiveId(id);
    const idx = routines.findIndex(r => r.scheduleId === id);
    if (idx >= 0) scrollToIdx(idx);
  }, [routines, scrollToIdx]);

  /* ── 루틴 저장 ── */
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

  /* ── 루틴 삭제 ── */
  const handleDeleteConfirm = () => {
    const id = deleteTarget.scheduleId;
    const prevIdx = routines.findIndex(r => r.scheduleId === id);
    setRoutines(prev => prev.filter(r => r.scheduleId !== id));
    const nextIdx = Math.max(0, prevIdx - 1);
    setTimeout(() => {
      const remaining = routines.filter(r => r.scheduleId !== id);
      if (remaining[nextIdx]) setActiveId(remaining[nextIdx].scheduleId);
      scrollToIdx(nextIdx);
    }, 50);
    setDeleteTarget(null);
  };

  const openAdd = () => setSheetState({ mode: 'add', init: { ...EMPTY_FORM } });
  const totalCards = routines.length + 1;

  return (
    <div className="cv5">

      {/* ── 전체 배경 지도 ── */}
      <RoutineMap
        routines={routines}
        activeId={activeId}
        onPinClick={handlePinClick}
      />

      {/* ── 상단 바 ── */}
      <header className="cv5-topbar">
        <button className="cv5-topbar__back" onClick={() => navigate('/v5')}>←</button>
        <span className="cv5-topbar__title">루틴</span>
        <span className="cv5-topbar__count">{routines.length}개</span>
      </header>

      {/* ── 하단 카드 영역 ── */}
      <div className="cv5-bottom">

        {/* 점 인디케이터 */}
        <div className="cv5-dots">
          {Array.from({ length: totalCards }).map((_, i) => (
            <button
              key={i}
              className={`cv5-dot${i === scrollIdx ? ' cv5-dot--on' : ''}`}
              onClick={() => {
                scrollToIdx(i);
                if (routines[i]) setActiveId(routines[i].scheduleId);
              }}
            />
          ))}
        </div>

        {/* 가로 스크롤 카드 */}
        <div className="cv5-scroll" ref={scrollRef}>
          {routines.map(routine => (
            <RoutineCard
              key={routine.scheduleId}
              routine={routine}
              todayDow={todayDow}
              isActive={routine.scheduleId === activeId}
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
        <button className="cv5-fab" onClick={openAdd}>+</button>
      )}

      {/* ── 바텀시트 ── */}
      {sheetState && (
        <RoutineSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={handleSave}
          onCancel={() => setSheetState(null)}
        />
      )}

      {/* ── 삭제 확인 ── */}
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
