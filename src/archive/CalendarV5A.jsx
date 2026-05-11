import React, {
  useState, useEffect, useRef, useCallback, useMemo,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { mockScheduleList } from '../data/mockData';
import './CalendarV5A.css';

/* ── Constants ─────────────────────────────────────────────────── */

const WEEK = [
  { eng: 'MON', ko: '월' }, { eng: 'TUE', ko: '화' }, { eng: 'WED', ko: '수' },
  { eng: 'THU', ko: '목' }, { eng: 'FRI', ko: '금' }, { eng: 'SAT', ko: '토' },
  { eng: 'SUN', ko: '일' },
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

function shortenName(n = '') {
  return n.replace('대학교', '대').replace('입구역', '역').replace('정류장', '').slice(0, 7);
}

/* quadratic bezier arc */
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

/* Attach geo coords to each schedule */
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

/* ── Map component ─────────────────────────────────────────────── */

function RoutineMap({
  routines, activeId, onPinClick, addTempOrigin, addTempDest, imperativeRef,
}) {
  const containerRef  = useRef(null);
  const kakaoMapRef   = useRef(null);
  const overlaysRef   = useRef([]);
  const polylinesRef  = useRef([]);
  const tempOvsRef    = useRef([]);
  const tempPolyRef   = useRef(null);
  const [mapReady, setMapReady]   = useState(false);
  const [loadError, setLoadError] = useState(false);

  /* ── Init map (once) ── */
  useEffect(() => {
    let cancelled = false;
    function init() {
      const kakao = window.kakao;
      if (!kakao?.maps) { setLoadError(true); return; }
      kakao.maps.load(() => {
        if (cancelled || !containerRef.current) return;
        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(37.58, 127.00),
          level:  7,
        });
        kakaoMapRef.current = map;

        /* Expose imperative API to parent */
        if (imperativeRef) {
          imperativeRef.current = {
            getScreenPos(lat, lng) {
              const proj = map.getProjection();
              const pt   = proj.containerPointFromCoords(new kakao.maps.LatLng(lat, lng));
              return { x: pt.x, y: pt.y };
            },
            addMoveListener(fn) {
              kakao.maps.event.addListener(map, 'center_changed', fn);
              kakao.maps.event.addListener(map, 'zoom_changed',   fn);
            },
            removeMoveListener(fn) {
              kakao.maps.event.removeListener(map, 'center_changed', fn);
              kakao.maps.event.removeListener(map, 'zoom_changed',   fn);
            },
            addClickListener(fn)    { kakao.maps.event.addListener(map,    'click', fn); },
            removeClickListener(fn) { kakao.maps.event.removeListener(map, 'click', fn); },
          };
        }
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

  /* ── Draw routine pins + arcs ── */
  useEffect(() => {
    const map = kakaoMapRef.current;
    if (!map || !mapReady) return;
    const kakao = window.kakao;

    overlaysRef.current.forEach(o => o.setMap(null));
    polylinesRef.current.forEach(p => p.setMap(null));
    overlaysRef.current = [];
    polylinesRef.current = [];

    if (routines.length === 0) return;

    const bounds = new kakao.maps.LatLngBounds();
    const drawnOrigins = new Set();

    routines.forEach((r, idx) => {
      const isActive = r.scheduleId === activeId;
      bounds.extend(new kakao.maps.LatLng(r._orig.lat, r._orig.lng));
      bounds.extend(new kakao.maps.LatLng(r._dest.lat, r._dest.lng));

      /* origin pin — deduplicated by coords */
      const origKey = `${r._orig.lat},${r._orig.lng}`;
      if (!drawnOrigins.has(origKey)) {
        drawnOrigins.add(origKey);
        const el = document.createElement('div');
        el.style.cssText = 'cursor:default;pointer-events:none;';
        el.innerHTML = `<div style="
          display:flex;align-items:center;gap:6px;
          background:rgba(255,255,255,0.92);backdrop-filter:blur(8px);
          padding:5px 12px 5px 8px;border-radius:24px;
          box-shadow:0 2px 12px rgba(0,0,0,0.12);
          font-family:'Noto Sans KR',sans-serif;white-space:nowrap;">
          <div style="width:8px;height:8px;background:#1a1a1a;border-radius:50%;flex-shrink:0;"></div>
          <span style="font-size:11px;font-weight:600;color:#1a1a1a;">${r.origin.name}</span>
        </div>`;
        overlaysRef.current.push(new kakao.maps.CustomOverlay({
          map, content: el, zIndex: 5,
          position: new kakao.maps.LatLng(r._orig.lat, r._orig.lng),
          yAnchor: 0.5, xAnchor: 0.5,
        }));
      }

      /* destination pin — clickable */
      const destEl = document.createElement('div');
      destEl.style.cssText = 'cursor:pointer;';
      destEl.innerHTML = `<div style="
        display:flex;align-items:center;gap:6px;
        background:${isActive ? '#3B82F6' : 'rgba(255,255,255,0.88)'};
        backdrop-filter:blur(8px);
        padding:5px 12px 5px 8px;border-radius:24px;
        box-shadow:0 2px 12px ${isActive ? 'rgba(59,130,246,0.35)' : 'rgba(0,0,0,0.1)'};
        font-family:'Noto Sans KR',sans-serif;white-space:nowrap;
        animation:cv5a-pin-drop 0.4s cubic-bezier(0.34,1.56,0.64,1) both;
        animation-delay:${idx * 0.12}s;
        transition:background 0.3s,box-shadow 0.3s;">
        <div style="width:8px;height:8px;background:${isActive ? 'white' : '#888'};border-radius:50%;flex-shrink:0;transition:background 0.3s;"></div>
        <span style="font-size:11px;font-weight:600;color:${isActive ? 'white' : '#666'};transition:color 0.3s;">${shortenName(r.destination.name)}</span>
      </div>`;
      destEl.addEventListener('click', (e) => {
        e.stopPropagation();
        onPinClick(r.scheduleId, r._dest);
      });
      overlaysRef.current.push(new kakao.maps.CustomOverlay({
        map, content: destEl, zIndex: 10,
        position: new kakao.maps.LatLng(r._dest.lat, r._dest.lng),
        yAnchor: 0.5, xAnchor: 0.5,
      }));

      /* arc polyline */
      const arc = arcPath(r._orig, r._dest);
      polylinesRef.current.push(new kakao.maps.Polyline({
        map,
        path:          arc.map(p => new kakao.maps.LatLng(p.lat, p.lng)),
        strokeColor:   '#3B82F6',
        strokeWeight:  isActive ? 4 : 2,
        strokeOpacity: isActive ? 0.8 : 0.22,
        strokeStyle:   'solid',
      }));
    });

    map.setBounds(bounds, 80, 40, 40, 40);
  }, [routines, activeId, mapReady]); // eslint-disable-line

  /* ── Add-mode temp pins ── */
  useEffect(() => {
    const map = kakaoMapRef.current;
    if (!map) return;
    const kakao = window.kakao;

    tempOvsRef.current.forEach(o => o.setMap(null));
    tempOvsRef.current = [];
    if (tempPolyRef.current) { tempPolyRef.current.setMap(null); tempPolyRef.current = null; }

    function tempPin(coords, label, bg, fg, dotColor) {
      const el = document.createElement('div');
      el.style.cssText = 'pointer-events:none;';
      el.innerHTML = `<div style="
        display:flex;align-items:center;gap:6px;
        background:${bg};backdrop-filter:blur(8px);
        padding:5px 12px 5px 8px;border-radius:24px;
        box-shadow:0 2px 12px rgba(0,0,0,0.15);
        font-family:'Noto Sans KR',sans-serif;white-space:nowrap;
        animation:cv5a-pin-drop 0.3s cubic-bezier(0.34,1.56,0.64,1);">
        <div style="width:8px;height:8px;background:${dotColor};border-radius:50%;"></div>
        <span style="font-size:11px;font-weight:600;color:${fg};">${label}</span>
      </div>`;
      const ov = new kakao.maps.CustomOverlay({
        map, content: el, zIndex: 15,
        position: new kakao.maps.LatLng(coords.lat, coords.lng),
        yAnchor: 0.5, xAnchor: 0.5,
      });
      tempOvsRef.current.push(ov);
    }

    if (addTempOrigin) {
      tempPin(addTempOrigin, '출발지', 'rgba(255,255,255,0.92)', '#1a1a1a', '#1a1a1a');
    }
    if (addTempDest) {
      tempPin(addTempDest, '도착지', '#3B82F6', 'white', 'white');
      if (addTempOrigin) {
        const arc = arcPath(addTempOrigin, addTempDest);
        tempPolyRef.current = new kakao.maps.Polyline({
          map,
          path:          arc.map(p => new kakao.maps.LatLng(p.lat, p.lng)),
          strokeColor:   '#3B82F6',
          strokeWeight:  3,
          strokeOpacity: 0.55,
          strokeStyle:   'solid',
        });
      }
    }

    return () => {
      tempOvsRef.current.forEach(o => o.setMap(null));
      if (tempPolyRef.current) tempPolyRef.current.setMap(null);
    };
  }, [addTempOrigin, addTempDest]); // eslint-disable-line

  return (
    <div className="cv5a-map" ref={containerRef}>
      {!mapReady && !loadError && (
        <div className="cv5a-map__loading">
          <div className="cv5a-spinner" />
          <span>지도 불러오는 중…</span>
        </div>
      )}
      {loadError && (
        <div className="cv5a-map__loading">
          <span>지도를 불러올 수 없어요</span>
        </div>
      )}
    </div>
  );
}

/* ── Pin Popup ─────────────────────────────────────────────────── */

function RoutinePopup({ routine, pos, onClose, onEdit, onDelete }) {
  const active  = routine.routineRule?.daysOfWeek ?? [];
  const arrTime = isoToHHMM(routine.arrivalTime);
  const depTime = calcDepTime(routine.arrivalTime, routine.averageDurationMinutes);

  /* flip below pin if near screen top */
  const POPUP_H = 230;
  const aboveY  = pos.y - POPUP_H - 28;
  const belowY  = pos.y + 28;
  const top     = aboveY < 72 ? belowY : aboveY;

  const style = {
    left: `${Math.max(10, Math.min(pos.x, window.innerWidth - 280))}px`,
    top:  `${top}px`,
  };

  return (
    <>
      <div className="cv5a-popup-bd" onClick={onClose} />
      <div className="cv5a-popup" style={style}>
        {/* Header */}
        <div className="cv5a-popup__head">
          <div>
            <p className="cv5a-popup__title">{routine.title}</p>
            <p className="cv5a-popup__arrive">{arrTime} 도착</p>
          </div>
          <button className="cv5a-popup__close" onClick={onClose}>×</button>
        </div>

        {/* Days of week */}
        <div className="cv5a-popup__days">
          {WEEK.map(({ eng, ko }) => (
            <span
              key={eng}
              className={`cv5a-popup__day${active.includes(eng) ? ' cv5a-popup__day--on' : ''}`}
            >
              {ko}
            </span>
          ))}
        </div>

        {/* Route summary */}
        <p className="cv5a-popup__summary">
          {routine.averageDurationMinutes}분 · 환승 1회 · 도보 350m
        </p>
        <p className="cv5a-popup__depart">{depTime} 출발 · 여유 30분</p>

        {/* Action buttons */}
        <div className="cv5a-popup__actions">
          <button className="cv5a-popup__btn cv5a-popup__btn--route">경로 보기</button>
          <button className="cv5a-popup__btn cv5a-popup__btn--edit" onClick={() => onEdit(routine)}>수정</button>
          <button className="cv5a-popup__btn cv5a-popup__btn--del"  onClick={() => onDelete(routine)}>삭제</button>
        </div>
      </div>
    </>
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
    <div className="cv5a-overlay" onClick={onCancel}>
      <div className="cv5a-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv5a-sheet__handle" />
        <p className="cv5a-sheet__heading">{mode === 'add' ? '새 루틴 추가' : '루틴 수정'}</p>

        <input
          className="cv5a-sheet__input"
          placeholder="루틴 이름"
          value={form.title}
          onChange={e => set('title', e.target.value)}
        />
        <input
          className="cv5a-sheet__input"
          placeholder="출발지"
          value={form.originName}
          onChange={e => set('originName', e.target.value)}
        />
        <input
          className="cv5a-sheet__input"
          placeholder="도착지"
          value={form.destinationName}
          onChange={e => set('destinationName', e.target.value)}
        />

        <div className="cv5a-sheet__time-wrap">
          <input
            className="cv5a-sheet__input cv5a-sheet__input--time"
            type="time"
            value={form.arrivalTime}
            onChange={e => set('arrivalTime', e.target.value)}
          />
          <span className="cv5a-sheet__hint">도착 희망 시간</span>
        </div>

        <p className="cv5a-sheet__days-label">반복 요일</p>
        <div className="cv5a-sheet__days">
          {WEEK.map(({ eng, ko }) => (
            <button
              key={eng}
              className={`cv5a-sheet__day${form.daysOfWeek.includes(eng) ? ' cv5a-sheet__day--on' : ''}`}
              onClick={() => toggleDay(eng)}
            >
              {ko}
            </button>
          ))}
        </div>

        <div className="cv5a-sheet__footer">
          <button className="cv5a-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv5a-sheet__save"   onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── Delete Confirm ────────────────────────────────────────────── */

function DeleteDialog({ routine, onConfirm, onCancel }) {
  return (
    <div className="cv5a-overlay cv5a-overlay--center" onClick={onCancel}>
      <div className="cv5a-dialog" onClick={e => e.stopPropagation()}>
        <p className="cv5a-dialog__msg">이 루틴을 삭제할까요?</p>
        <p className="cv5a-dialog__name">{routine.title}</p>
        <div className="cv5a-dialog__btns">
          <button className="cv5a-dialog__cancel" onClick={onCancel}>취소</button>
          <button className="cv5a-dialog__del"    onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   CalendarV5A — Main
   ================================================================ */

export default function CalendarV5A() {
  const navigate = useNavigate();

  const [routines,     setRoutines]     = useState(INITIAL_ROUTINES);
  const [activeId,     setActiveId]     = useState(null);
  const [popupPos,     setPopupPos]     = useState(null);  // { x, y }
  const [sheetState,   setSheetState]   = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [addStep,      setAddStep]      = useState(null);  // null | 'origin' | 'dest'
  const [tempOrigin,   setTempOrigin]   = useState(null);
  const [tempDest,     setTempDest]     = useState(null);

  const imperativeRef = useRef(null);
  const activeIdRef   = useRef(null);
  activeIdRef.current = activeId;

  /* ── Pin click ── */
  const handlePinClick = useCallback((id, destCoords) => {
    if (activeIdRef.current === id) {
      setActiveId(null);
      setPopupPos(null);
      return;
    }
    setActiveId(id);
    if (imperativeRef.current) {
      setPopupPos(imperativeRef.current.getScreenPos(destCoords.lat, destCoords.lng));
    }
  }, []);

  /* ── Keep popup pos in sync while map moves ── */
  useEffect(() => {
    if (!activeId || !imperativeRef.current) return;
    const routine = routines.find(r => r.scheduleId === activeId);
    if (!routine) return;
    const fn = () => {
      const pos = imperativeRef.current?.getScreenPos(routine._dest.lat, routine._dest.lng);
      if (pos) setPopupPos(pos);
    };
    imperativeRef.current.addMoveListener(fn);
    return () => imperativeRef.current?.removeMoveListener(fn);
  }, [activeId, routines]);

  /* ── Map click in add mode ── */
  useEffect(() => {
    if (!addStep || !imperativeRef.current) return;

    const capturedOrigin = tempOrigin;  // capture for closure

    const handler = (mouseEvent) => {
      const latlng = mouseEvent.latLng;
      const coords = { lat: latlng.getLat(), lng: latlng.getLng() };

      if (addStep === 'origin') {
        setTempOrigin(coords);
        setAddStep('dest');
      } else {
        setTempDest(coords);
        setAddStep(null);
        setSheetState({ mode: 'add', init: { ...EMPTY_FORM }, _origCoords: capturedOrigin, _destCoords: coords });
      }
    };

    imperativeRef.current.addClickListener(handler);
    return () => imperativeRef.current?.removeClickListener(handler);
  }, [addStep, tempOrigin]);

  /* ── Start add mode ── */
  const startAdd = () => {
    setActiveId(null); setPopupPos(null); setSheetState(null);
    setTempOrigin(null); setTempDest(null);
    setAddStep('origin');
  };

  const cancelAdd = () => {
    setAddStep(null); setTempOrigin(null); setTempDest(null); setSheetState(null);
  };

  /* ── Save routine ── */
  const handleSave = (form) => {
    if (sheetState.mode === 'add') {
      const [h, m] = form.arrivalTime.split(':');
      const base = new Date(); base.setHours(+h, +m, 0, 0);
      const origC = sheetState._origCoords ?? { lat: 37.5665, lng: 126.9780 };
      const destC = sheetState._destCoords ?? { lat: 37.5700, lng: 126.9800 };
      setRoutines(prev => [...prev, {
        scheduleId: `sch_${Date.now()}`,
        title: form.title || '새 루틴',
        origin:      { name: form.originName      || '출발지', ...origC },
        destination: { name: form.destinationName || '도착지', ...destC },
        arrivalTime: base.toISOString(), reminderAt: base.toISOString(),
        averageDurationMinutes: 30,
        routineRule: { type: 'WEEKLY', daysOfWeek: form.daysOfWeek.length > 0 ? form.daysOfWeek : ['MON'] },
        _orig: origC, _dest: destC,
      }]);
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
    setSheetState(null); setTempOrigin(null); setTempDest(null);
  };

  /* ── Delete routine ── */
  const handleDeleteConfirm = () => {
    const id = deleteTarget.scheduleId;
    if (activeId === id) { setActiveId(null); setPopupPos(null); }
    setRoutines(prev => prev.filter(r => r.scheduleId !== id));
    setDeleteTarget(null);
  };

  const isAddMode    = addStep !== null;
  const activeRoutine = useMemo(() => routines.find(r => r.scheduleId === activeId) ?? null, [routines, activeId]);

  return (
    <div className="cv5a">

      {/* ── Full-screen map ── */}
      <RoutineMap
        routines={routines}
        activeId={activeId}
        onPinClick={handlePinClick}
        addTempOrigin={tempOrigin}
        addTempDest={tempDest}
        imperativeRef={imperativeRef}
      />

      {/* ── Top bar ── */}
      <header className={`cv5a-topbar${isAddMode ? ' cv5a-topbar--add' : ''}`}>
        <button
          className="cv5a-topbar__back"
          onClick={() => isAddMode ? cancelAdd() : navigate('/v5')}
        >
          ←
        </button>
        <span className="cv5a-topbar__title">
          {isAddMode
            ? (addStep === 'origin' ? '출발지를 탭하세요' : '도착지를 탭하세요')
            : '루틴 관리'
          }
        </span>
        <span className="cv5a-topbar__count">
          {!isAddMode && `${routines.length}개`}
        </span>
      </header>

      {/* ── Pin popup ── */}
      {activeRoutine && popupPos && !sheetState && !deleteTarget && (
        <RoutinePopup
          routine={activeRoutine}
          pos={popupPos}
          onClose={() => { setActiveId(null); setPopupPos(null); }}
          onEdit={r => {
            setSheetState({
              mode: 'edit', routineId: r.scheduleId,
              init: {
                title: r.title,
                originName: r.origin.name,
                destinationName: r.destination.name,
                arrivalTime: isoToHHMM(r.arrivalTime),
                daysOfWeek: r.routineRule?.daysOfWeek ?? [],
              },
            });
            setActiveId(null); setPopupPos(null);
          }}
          onDelete={r => {
            setDeleteTarget(r);
            setActiveId(null); setPopupPos(null);
          }}
        />
      )}

      {/* ── FAB ── */}
      {!isAddMode && !sheetState && !deleteTarget && (
        <button className="cv5a-fab" onClick={startAdd} title="루틴 추가">+</button>
      )}

      {/* ── Bottom sheet ── */}
      {sheetState && (
        <RoutineSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={handleSave}
          onCancel={() => {
            setSheetState(null);
            if (sheetState.mode === 'add') { setTempOrigin(null); setTempDest(null); }
          }}
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
