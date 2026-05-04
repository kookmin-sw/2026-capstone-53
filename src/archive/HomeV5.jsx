import React, {
  useState, useEffect, useRef, useMemo, useCallback,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Settings } from 'lucide-react';
import {
  mockMainData,
  mockRouteData,
  mockMember,
  mockScheduleList,
} from '../data/mockData';

import './HomeV5.css';

// 일정 ID → 경로 후보 매핑 (실제 API 연동 전까지 mock)
const ROUTE_BY_ID = {
  [mockScheduleList.data[0].scheduleId]: mockRouteData.data.candidates[0],
};

/* ================================================================
   상수 / 유틸
   ================================================================ */

const PANEL_SNAPS = [160, 340, 520];
const CIRC = 2 * Math.PI * 24; // r=24 → ≈150.8

function formatDateKo(d) {
  const DAYS = ['일', '월', '화', '수', '목', '금', '토'];
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${DAYS[d.getDay()]}`;
}

function isoToHHMM(iso) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function calcCountdown(reminderAt) {
  const now = new Date();
  const src = new Date(reminderAt);
  const t = new Date(
    now.getFullYear(), now.getMonth(), now.getDate(),
    src.getHours(), src.getMinutes(), 0,
  );
  const diff = t - now;
  return diff > 0 ? Math.ceil(diff / 60_000) : 0;
}

// 구간 칩 라벨 — 이모지 없이 교통수단 + 시간 텍스트
function chipLabel(seg) {
  if (seg.mode === 'WALK') return `도보 ${seg.durationMinutes}분`;
  if (seg.mode === 'SUBWAY') return `${seg.lineName ?? '지하철'} ${seg.durationMinutes}분`;
  return `${seg.lineName ?? '버스'}번 ${seg.durationMinutes}분`;
}

/* ── 커스텀 오버레이 HTML 생성 함수 (인라인 스타일 알약 형태) ── */

// 긴 장소명 축약 (최대 5자)
function shortenName(name) {
  if (!name) return '';
  return name.replace('대학교', '대').replace('입구역', '역').replace('정류장', '');
}

function markerStartHtml() {
  return `<div style="
    display:flex;align-items:center;gap:6px;
    background:white;padding:6px 14px 6px 8px;
    border-radius:24px;box-shadow:0 2px 12px rgba(0,0,0,0.15);
    font-family:'Noto Sans KR',sans-serif;white-space:nowrap;
  ">
    <div style="width:10px;height:10px;background:#3B82F6;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:12px;font-weight:600;color:#1a1a1a;">출발</span>
  </div>`;
}

function markerEndHtml(name) {
  return `<div style="
    display:flex;align-items:center;gap:6px;
    background:#3B82F6;padding:6px 14px 6px 8px;
    border-radius:24px;box-shadow:0 2px 12px rgba(59,130,246,0.3);
    font-family:'Noto Sans KR',sans-serif;white-space:nowrap;
  ">
    <div style="width:10px;height:10px;background:white;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:12px;font-weight:600;color:white;">${shortenName(name)}</span>
  </div>`;
}

function markerTransferHtml(name) {
  return `<div style="
    display:flex;align-items:center;gap:4px;
    background:white;padding:4px 10px 4px 6px;
    border-radius:20px;box-shadow:0 1px 6px rgba(0,0,0,0.1);
    font-family:'Noto Sans KR',sans-serif;white-space:nowrap;
  ">
    <div style="width:6px;height:6px;background:#888;border-radius:50%;flex-shrink:0;"></div>
    <span style="font-size:10px;font-weight:500;color:#888;">${shortenName(name)}</span>
  </div>`;
}

function buildTimeline(segments) {
  const stops = [];
  const s0 = segments[0];
  stops.push({
    name: s0.from,
    detail: s0.mode === 'WALK'
      ? `도보 ${s0.durationMinutes}분 · ${s0.distanceMeters ?? 0}m`
      : `${s0.lineName ?? ''} · ${s0.durationMinutes}분`,
    type: 'start',
  });
  for (let i = 1; i < segments.length; i++) {
    const s = segments[i];
    stops.push({
      name: s.from,
      detail: s.mode === 'WALK'
        ? `도보 ${s.durationMinutes}분 · ${s.distanceMeters ?? 0}m`
        : s.mode === 'BUS'
          ? `${s.lineName ?? ''}번 버스 · ${s.durationMinutes}분`
          : `${s.lineName ?? ''} · ${s.durationMinutes}분`,
      type: 'mid',
    });
  }
  stops.push({ name: segments[segments.length - 1].to, detail: '', type: 'end' });
  return stops;
}

/* ================================================================
   카운트다운 원형 SVG
   ================================================================ */

function CountdownCircle({ minutes }) {
  const [offset, setOffset] = useState(CIRC);

  useEffect(() => {
    const id = setTimeout(() => {
      setOffset(CIRC * (1 - Math.min(minutes / 60, 1)));
    }, 400);
    return () => clearTimeout(id);
  }, [minutes]);

  const num = minutes < 60 ? String(minutes) : `${Math.floor(minutes / 60)}h`;

  return (
    <div className="hv5-circle">
      <svg viewBox="0 0 56 56" className="hv5-circle__svg">
        <circle cx="28" cy="28" r="24" className="hv5-circle__track" />
        <circle cx="28" cy="28" r="24" className="hv5-circle__ring"
          style={{ strokeDashoffset: offset }} />
      </svg>
      <div className="hv5-circle__inner">
        <span className="hv5-circle__num">{num}</span>
        <span className="hv5-circle__unit">min</span>
      </div>
    </div>
  );
}

/* ================================================================
   카카오맵 유틸
   ================================================================ */

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

/* ================================================================
   카카오맵 — 지도 초기화(1회) + 경로 업데이트(segments 변경마다) 분리
   ================================================================ */

function MapView({ segments }) {
  const mapDivRef   = useRef(null);
  const kakaoMapRef = useRef(null);   // kakao.maps.Map 인스턴스
  const polylinesRef = useRef([]);    // 현재 표시 중인 Polyline 목록
  const overlaysRef  = useRef([]);    // 현재 표시 중인 CustomOverlay 목록
  const rafRef       = useRef(null);  // 현재 RAF ID
  const [mapReady, setMapReady] = useState(false);
  const [loadError, setLoadError] = useState(false);

  /* ── 1. 지도 인스턴스 생성 (딱 한 번) ── */
  useEffect(() => {
    let cancelled = false;

    function createMap() {
      const kakao = window.kakao;
      if (!kakao?.maps) { setLoadError(true); return; }

      kakao.maps.load(() => {
        if (cancelled || !mapDivRef.current) return;
        const defaultCenter = new kakao.maps.LatLng(37.62, 127.0);
        kakaoMapRef.current = new kakao.maps.Map(mapDivRef.current, {
          center: defaultCenter,
          level: 6,
        });
        setMapReady(true);
      });
    }

    if (window.kakao?.maps) {
      createMap();
    } else {
      const timer = setInterval(() => {
        if (window.kakao?.maps) { clearInterval(timer); clearTimeout(tOut); createMap(); }
      }, 100);
      const tOut = setTimeout(() => {
        clearInterval(timer);
        if (!cancelled) setLoadError(true);
      }, 5000);
      return () => { cancelled = true; clearInterval(timer); clearTimeout(tOut); };
    }

    return () => { cancelled = true; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── 2. 경로 + 마커 업데이트 (segments 또는 mapReady 변경 시) ── */
  useEffect(() => {
    const map = kakaoMapRef.current;
    if (!map || !mapReady) return;

    const kakao = window.kakao;
    if (!kakao?.maps) return;

    // 이전 애니메이션 취소
    if (rafRef.current) { cancelAnimationFrame(rafRef.current); rafRef.current = null; }

    // 이전 폴리라인 / 오버레이 제거
    polylinesRef.current.forEach(p => p.setMap(null));
    overlaysRef.current.forEach(o => o.setMap(null));
    polylinesRef.current = [];
    overlaysRef.current = [];

    // 경로 없음 → 기본 중심만 유지
    if (!segments || segments.length === 0) return;

    const densePaths = segments.map(seg => densifyPath(seg.path ?? [], 60));

    // fitBounds
    const bounds = new kakao.maps.LatLngBounds();
    densePaths.forEach(path =>
      path.forEach(([lng, lat]) => bounds.extend(new kakao.maps.LatLng(lat, lng)))
    );
    map.setBounds(bounds, 80, 24, 300, 80);

    // ── 마커 ──
    const addOverlay = (opts) => {
      const o = new kakao.maps.CustomOverlay(opts);
      overlaysRef.current.push(o);
      return o;
    };

    // 출발
    const fp = densePaths[0][0];
    addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
      position: new kakao.maps.LatLng(fp[1], fp[0]),
      content: markerStartHtml(),
    });

    // 도착
    const lp = densePaths[densePaths.length - 1];
    const ep = lp[lp.length - 1];
    addOverlay({ map, zIndex: 10, yAnchor: 0.5, xAnchor: 0.5,
      position: new kakao.maps.LatLng(ep[1], ep[0]),
      content: markerEndHtml(segments[segments.length - 1].to),
    });

    // 환승 (교통수단 전환점, 마지막 WALK 시작점 제외)
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

    // ── 폴리라인 ──
    const polylines = segments.map(seg =>
      new kakao.maps.Polyline({
        map, path: [],
        strokeWeight: seg.mode === 'WALK' ? 3 : 4,
        strokeColor: '#3B82F6', strokeOpacity: 0.8,
        strokeStyle: seg.mode === 'WALK' ? 'shortdash' : 'solid',
      })
    );
    polylinesRef.current = polylines;

    // ── 이동 점 ──
    const dotDiv = document.createElement('div');
    dotDiv.style.cssText = [
      'width:12px', 'height:12px', 'background:#3B82F6',
      'border-radius:50%', 'border:2px solid white',
      'box-shadow:0 0 8px rgba(59,130,246,0.4)',
      'transform:translate(-50%,-50%)',
    ].join(';');
    const dotOverlay = addOverlay({ content: dotDiv, zIndex: 15 });

    // ── 경로 그리기 → 이동 점 루프 애니메이션 ──
    const allDense = densePaths.flat();
    const totalPts = allDense.length;
    const DRAW_DUR = 2000, MOVE_DUR = 4000;
    let startTime = null, phase = 'drawing';
    let localCancelled = false;

    function animate(ts) {
      if (localCancelled) return;
      if (phase === 'drawing') {
        if (!startTime) startTime = ts;
        const raw    = Math.min((ts - startTime) / DRAW_DUR, 1);
        const target = Math.floor(easeInOut(raw) * totalPts);
        let drawn = 0;
        for (let si = 0; si < densePaths.length; si++) {
          if (drawn >= target) break;
          const take = Math.min(target - drawn, densePaths[si].length);
          polylines[si].setPath(
            densePaths[si].slice(0, take).map(([lng, lat]) => new kakao.maps.LatLng(lat, lng))
          );
          drawn += take;
        }
        if (raw < 1) {
          rafRef.current = requestAnimationFrame(animate);
        } else {
          phase = 'moving'; startTime = null;
          dotOverlay.setMap(map);
          rafRef.current = requestAnimationFrame(animate);
        }
      } else {
        if (!startTime) startTime = ts;
        const loopP = ((ts - startTime) % MOVE_DUR) / MOVE_DUR;
        const idx = Math.min(Math.floor(loopP * totalPts), totalPts - 1);
        const [lng, lat] = allDense[idx];
        dotOverlay.setPosition(new kakao.maps.LatLng(lat, lng));
        rafRef.current = requestAnimationFrame(animate);
      }
    }

    rafRef.current = requestAnimationFrame(animate);

    return () => { localCancelled = true; };
  }, [segments, mapReady]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="hv5-map">
      <div ref={mapDivRef} style={{ width: '100%', height: '100%' }} />
      {!mapReady && !loadError && (
        <div className="hv5-map-overlay">
          <div className="hv5-map-spinner" />
          <span>지도 불러오는 중…</span>
        </div>
      )}
      {loadError && (
        <div className="hv5-map-overlay">
          <span>지도를 불러올 수 없어요</span>
        </div>
      )}
    </div>
  );
}

/* ================================================================
   일정 탭
   ================================================================ */

function ScheduleTabs({ schedules, selectedId, onSelect }) {
  if (schedules.length <= 1) return null;
  return (
    <div className="hv5-tabs">
      {schedules.map(s => (
        <button
          key={s.scheduleId}
          className={`hv5-tab${s.scheduleId === selectedId ? ' hv5-tab--active' : ''}`}
          onClick={() => onSelect(s.scheduleId)}
        >
          {s.title}
        </button>
      ))}
    </div>
  );
}

/* ================================================================
   하단 슬라이드업 패널
   ================================================================ */

function BottomPanel({ schedule, cand, segments, timeline, schedules, selectedId, onSelectSchedule }) {
  const navigate = useNavigate();

  // 드래그 상태
  const [snapIdx, setSnapIdx]     = useState(0);
  const [height, setHeight]       = useState(PANEL_SNAPS[0]);
  const [isDragging, setIsDragging] = useState(false);
  const heightRef = useRef(PANEL_SNAPS[0]);
  const startYRef = useRef(0);
  const startHRef = useRef(0);

  // 카운트다운
  const [countdown, setCountdown] = useState(() => calcCountdown(schedule.reminderAt));
  useEffect(() => {
    const id = setInterval(() => setCountdown(calcCountdown(schedule.reminderAt)), 60_000);
    return () => clearInterval(id);
  }, [schedule.reminderAt]);

  const totalWalk = useMemo(
    () => (segments ?? []).filter(s => s.mode === 'WALK').reduce((acc, s) => acc + (s.distanceMeters ?? 0), 0),
    [segments],
  );

  // 탭 전환 시 콘텐츠 fade
  const [fade, setFade] = useState(false);
  const prevIdRef = useRef(selectedId);
  useEffect(() => {
    if (prevIdRef.current === selectedId) return;
    prevIdRef.current = selectedId;
    setFade(true);
    const id = setTimeout(() => setFade(false), 200);
    return () => clearTimeout(id);
  }, [selectedId]);

  const snapTo = useCallback((idx) => {
    heightRef.current = PANEL_SNAPS[idx];
    setSnapIdx(idx);
    setHeight(PANEL_SNAPS[idx]);
  }, []);

  // 핸들 포인터 이벤트 (mouse + touch)
  const onPointerDown = useCallback((e) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    startYRef.current = e.clientY;
    startHRef.current = heightRef.current;
    setIsDragging(true);
  }, []);

  const onPointerMove = useCallback((e) => {
    if (!e.currentTarget.hasPointerCapture(e.pointerId)) return;
    const delta = startYRef.current - e.clientY;
    const newH = Math.max(PANEL_SNAPS[0], Math.min(PANEL_SNAPS[2], startHRef.current + delta));
    heightRef.current = newH;
    setHeight(newH);
  }, []);

  const onPointerUp = useCallback(() => {
    setIsDragging(false);
    const h = heightRef.current;
    let closest = 0;
    let minD = Infinity;
    PANEL_SNAPS.forEach((snap, i) => {
      const d = Math.abs(h - snap);
      if (d < minD) { minD = d; closest = i; }
    });
    snapTo(closest);
  }, [snapTo]);

  const panelStyle = {
    height: `${height}px`,
    transition: isDragging ? 'none' : 'height 0.3s ease',
  };

  return (
    <div className="hv5-panel" style={panelStyle}>
      {/* 드래그 핸들 */}
      <div
        className="hv5-panel__handle-area"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
      >
        <div className="hv5-panel__handle" />
      </div>

      {/* 일정 탭 — 핸들 바로 아래 */}
      <ScheduleTabs
        schedules={schedules}
        selectedId={selectedId}
        onSelect={onSelectSchedule}
      />

      {/* 스크롤 가능한 바디 */}
      <div className={`hv5-panel__body${fade ? ' hv5-panel__body--fade' : ''}`}>

        {/* ── 항상 보이는 영역: 일정 요약 + 구간 칩 ── */}
        <div className="hv5-panel__summary">
          <div className="hv5-panel__summary-left">
            <p className="hv5-panel__title">{schedule.title}</p>
            <p className="hv5-panel__route">
              {schedule.origin?.name ?? '출발지'} → {schedule.destination?.name ?? '도착지'}
            </p>
          </div>
          <span className="hv5-panel__arrival-time">
            {schedule.arrivalTime.substring(11, 16)}
          </span>
        </div>

        {segments && segments.length > 0 && (
          <div className="hv5-chips">
            {segments.map((seg, i) => (
              <React.Fragment key={i}>
                <div className="hv5-chip">
                  <span className="hv5-chip__label">{chipLabel(seg)}</span>
                </div>
                {i < segments.length - 1 && (
                  <span className="hv5-chips__arrow">→</span>
                )}
              </React.Fragment>
            ))}
          </div>
        )}

        {/* 접힌 상태 힌트 */}
        {snapIdx === 0 && (
          <div className="hv5-panel__hint" onClick={() => snapTo(1)}>
            <span className="hv5-panel__hint-arrow">^</span>
            <span className="hv5-panel__hint-text">자세히 보기</span>
          </div>
        )}

        {/* ── 중간 이상: 출발 안내 + 통계 ── */}
        {snapIdx >= 1 && (
          <>
            <div className="hv5-divider" />

            <div className="hv5-depart-block">
              <div className="hv5-depart-block__left">
                <span className="hv5-depart-block__label">출발</span>
                <span className="hv5-depart-block__time">{isoToHHMM(schedule.reminderAt)}</span>
                <span className="hv5-depart-block__hint">
                  여유 {schedule.reminderOffsetMinutes ?? 30}분 포함
                </span>
              </div>
              <CountdownCircle minutes={countdown} />
            </div>

            {cand && (
              <div className="hv5-stats">
                <div className="hv5-stat">
                  <span className="hv5-stat__num">{cand.totalDurationMinutes}</span>
                  <span className="hv5-stat__label">분 소요</span>
                </div>
                <div className="hv5-stat">
                  <span className="hv5-stat__num">{cand.totalTransfers}</span>
                  <span className="hv5-stat__label">환승</span>
                </div>
                <div className="hv5-stat">
                  <span className="hv5-stat__num">{totalWalk}</span>
                  <span className="hv5-stat__label">m 도보</span>
                </div>
              </div>
            )}
          </>
        )}

        {/* ── 펼친 상태: 타임라인 + 버튼 ── */}
        {snapIdx >= 2 && timeline && timeline.length > 0 && (
          <>
            <div className="hv5-divider" />
            <p className="hv5-tl-label">경로</p>

            <div className="hv5-timeline">
              {timeline.map((stop, i) => (
                <div key={i} className="hv5-tl-row">
                  <div className="hv5-tl-track">
                    <div className={`hv5-tl-dot hv5-tl-dot--${
                      stop.type === 'start' || stop.type === 'end' ? 'filled' : 'hollow'
                    }`} />
                    {i < timeline.length - 1 && <div className="hv5-tl-line" />}
                  </div>
                  <div className="hv5-tl-text">
                    <p className={`hv5-tl-name${stop.type === 'end' ? ' hv5-tl-name--accent' : ''}`}>
                      {stop.name}
                    </p>
                    {stop.detail && <p className="hv5-tl-detail">{stop.detail}</p>}
                  </div>
                </div>
              ))}
            </div>

            <button className="hv5-btn-nav" onClick={() => alert('네비게이션 시작')}>
              네비게이션 시작
            </button>
          </>
        )}

      </div>
    </div>
  );
}

/* ================================================================
   빈 상태 패널
   ================================================================ */

function EmptyPanel() {
  const navigate = useNavigate();
  return (
    <div className="hv5-panel hv5-panel--empty">
      <div className="hv5-panel__handle-area">
        <div className="hv5-panel__handle" />
      </div>
      <p className="hv5-empty__main">등록된 일정이 없어요</p>
      <button className="hv5-empty__cta" onClick={() => navigate('/calendar')}>
        캘린더에서 추가
      </button>
    </div>
  );
}

/* ================================================================
   메인 컴포넌트
   ================================================================ */

export default function HomeV5() {
  const navigate  = useNavigate();
  const now       = useMemo(() => new Date(), []);
  const nickname  = mockMember.data.nickname;
  const weather   = { temperature: 28 };

  // 모든 일정 탭 목록
  const schedules = mockScheduleList.data;

  // 선택된 일정 ID (기본: 첫 번째)
  const [selectedId, setSelectedId] = useState(schedules[0]?.scheduleId ?? null);

  // 선택된 일정 객체 (mockScheduleList 기반)
  const selectedScheduleBase = useMemo(
    () => schedules.find(s => s.scheduleId === selectedId) ?? schedules[0],
    [schedules, selectedId],
  );

  // 첫 번째 일정은 reminderOffsetMinutes 등 상세 데이터를 mockMainData에서 보완
  const schedule = useMemo(() => {
    const base = selectedScheduleBase;
    if (!base) return null;
    if (base.scheduleId === mockMainData.data.nearestSchedule?.scheduleId) {
      return { ...mockMainData.data.nearestSchedule, ...base };
    }
    return base;
  }, [selectedScheduleBase]);

  // 선택된 일정의 경로 후보 (없으면 null)
  const cand     = selectedId ? (ROUTE_BY_ID[selectedId] ?? null) : null;
  const segments = cand?.segments ?? null;
  const timeline = useMemo(
    () => (segments && segments.length > 0 ? buildTimeline(segments) : null),
    [segments],
  );

  return (
    <div className="hv5">

      {/* 지도 전체 배경 */}
      <MapView segments={segments} />

      {/* 상단 바 */}
      <header className="hv5-topbar">
        <div className="hv5-topbar__left">
          <span className="hv5-topbar__name">{nickname}</span>
          <span className="hv5-topbar__online" />
        </div>
        <div className="hv5-topbar__center">
          <span className="hv5-topbar__date">{formatDateKo(now)}</span>
        </div>
        <div className="hv5-topbar__right">
          <span className="hv5-topbar__temp">{weather.temperature}°</span>
        </div>
      </header>

      {/* 좌측 사이드 버튼 */}
      <div className="hv5-side">
        <button className="hv5-side__btn" onClick={() => navigate('/v5/calendar')} title="루틴 관리">
          <Calendar size={18} color="#3B82F6" strokeWidth={1.8} />
        </button>
        <button className="hv5-side__btn" onClick={() => navigate('/settings')} title="설정">
          <Settings size={18} color="#3B82F6" strokeWidth={1.8} />
        </button>
      </div>

      {/* 하단 패널 */}
      {schedule ? (
        <BottomPanel
          schedule={schedule}
          cand={cand}
          segments={segments}
          timeline={timeline}
          schedules={schedules}
          selectedId={selectedId}
          onSelectSchedule={setSelectedId}
        />
      ) : (
        <EmptyPanel />
      )}

    </div>
  );
}
