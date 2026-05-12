import React, { useEffect, useRef, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockMainData, mockRouteData } from '../data/mockData';
import './RouteMapV3.css';

/* ================================================================
   유틸
   ================================================================ */

function densifyPath(pathCoords, pointsPerSegment = 80) {
  if (pathCoords.length < 2) return pathCoords;
  const result = [];
  for (let i = 0; i < pathCoords.length - 1; i++) {
    const [lng1, lat1] = pathCoords[i];
    const [lng2, lat2] = pathCoords[i + 1];
    for (let j = 0; j < pointsPerSegment; j++) {
      const t = j / pointsPerSegment;
      result.push([lng1 + (lng2 - lng1) * t, lat1 + (lat2 - lat1) * t]);
    }
  }
  result.push(pathCoords[pathCoords.length - 1]);
  return result;
}

function easeInOut(t) {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

function isoToHHMM(iso) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function buildTimeline(segments) {
  const stops = [];
  const first = segments[0];
  stops.push({
    name:   first.from,
    detail: first.mode === 'WALK'
      ? `도보 ${first.durationMinutes}분 · ${first.distanceMeters}m`
      : `${first.lineName}번 버스 · ${first.durationMinutes}분`,
    type: 'start',
  });
  for (let i = 1; i < segments.length; i++) {
    const s = segments[i];
    stops.push({
      name: s.from,
      detail: s.mode === 'WALK'
        ? `도보 ${s.durationMinutes}분 · ${s.distanceMeters}m`
        : s.mode === 'BUS'
          ? `${s.lineName}번 버스 · ${s.durationMinutes}분`
          : `지하철 · ${s.durationMinutes}분`,
      type: 'mid',
    });
  }
  stops.push({ name: segments[segments.length - 1].to, detail: '', type: 'end' });
  return stops;
}

/* ================================================================
   마커 HTML 생성 (라벤더 테마)
   ================================================================ */

const ACCENT = '#8B7BB5';

function markerStart() {
  return `<div style="display:flex;flex-direction:column;align-items:center;">
    <div style="background:#0d0c0a;color:#fff;border-radius:8px;padding:3px 10px;font-size:11px;font-weight:700;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.22);font-family:-apple-system,sans-serif;">출발</div>
    <div style="width:10px;height:10px;background:#0d0c0a;border:2.5px solid white;border-radius:50%;margin-top:3px;box-shadow:0 1px 4px rgba(0,0,0,0.25);"></div>
  </div>`;
}

function markerEnd() {
  return `<div style="display:flex;flex-direction:column;align-items:center;">
    <div style="background:${ACCENT};color:#fff;border-radius:8px;padding:3px 10px;font-size:11px;font-weight:700;white-space:nowrap;box-shadow:0 2px 8px rgba(139,123,181,0.35);font-family:-apple-system,sans-serif;">도착</div>
    <div style="width:10px;height:10px;background:${ACCENT};border:2.5px solid white;border-radius:50%;margin-top:3px;box-shadow:0 1px 4px rgba(0,0,0,0.2);"></div>
  </div>`;
}

function markerTransfer() {
  return `<div style="width:6px;height:6px;background:#9CA3AF;border:2px solid white;border-radius:50%;box-shadow:0 1px 3px rgba(0,0,0,0.2);"></div>`;
}

/* ================================================================
   하단 시트 컴포넌트
   ================================================================ */

const SHEET_COLLAPSED = 140;
const SHEET_EXPANDED  = 420;
const SNAP_THRESHOLD  = 60;

function BottomSheet({ schedule, cand, timeline, busSegs, totalWalkingMeters }) {
  const [expanded, setExpanded]   = useState(false);
  const [dragging, setDragging]   = useState(false);
  const [sheetY,   setSheetY]     = useState(0);   // drag offset (positive = dragging down)
  const startYRef  = useRef(null);
  const startTopRef = useRef(null);

  const height = expanded ? SHEET_EXPANDED : SHEET_COLLAPSED;
  const displayH = Math.max(SHEET_COLLAPSED, height - sheetY);

  function onPointerDown(e) {
    setDragging(true);
    startYRef.current   = e.clientY ?? e.touches?.[0]?.clientY;
    startTopRef.current = expanded ? SHEET_EXPANDED : SHEET_COLLAPSED;
    e.currentTarget.setPointerCapture?.(e.pointerId);
  }

  function onPointerMove(e) {
    if (!dragging) return;
    const clientY = e.clientY ?? e.touches?.[0]?.clientY;
    const delta   = startYRef.current - clientY;   // positive → dragging up
    const next    = Math.max(SHEET_COLLAPSED, Math.min(SHEET_EXPANDED, startTopRef.current + delta));
    setSheetY(startTopRef.current - next);
  }

  function onPointerUp() {
    if (!dragging) return;
    setDragging(false);
    const cur = expanded ? SHEET_EXPANDED - sheetY : SHEET_COLLAPSED - sheetY;
    if (!expanded && cur > SHEET_COLLAPSED + SNAP_THRESHOLD) setExpanded(true);
    else if (expanded && cur < SHEET_EXPANDED - SNAP_THRESHOLD) setExpanded(false);
    setSheetY(0);
  }

  const departTime = isoToHHMM(schedule.reminderAt);
  const arriveTime = isoToHHMM(schedule.arrivalTime);

  return (
    <div
      className="rmv3-sheet"
      style={{ height: `${displayH}px`, transition: dragging ? 'none' : 'height 0.35s cubic-bezier(0.32,0,0.67,1)' }}
    >
      {/* 드래그 핸들 */}
      <div
        className="rmv3-sheet__handle"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onTouchStart={e => onPointerDown({ clientY: e.touches[0].clientY, currentTarget: e.currentTarget, touches: e.touches })}
        onTouchMove={e => onPointerMove({ touches: e.touches })}
        onTouchEnd={onPointerUp}
      >
        <div className="rmv3-sheet__pill" />
      </div>

      {/* 접힌 상태: 시간 + 요약 + 버스 칩 */}
      <div className="rmv3-sheet__collapsed">
        <div className="rmv3-sheet__times">
          <div className="rmv3-sheet__time-block">
            <span className="rmv3-sheet__time-label">출발</span>
            <span className="rmv3-sheet__time-value rmv3-sheet__time-value--depart">{departTime}</span>
          </div>
          <div className="rmv3-sheet__time-arrow">→</div>
          <div className="rmv3-sheet__time-block">
            <span className="rmv3-sheet__time-label">도착</span>
            <span className="rmv3-sheet__time-value">{arriveTime}</span>
          </div>
        </div>

        <div className="rmv3-sheet__summary">
          <span className="rmv3-sheet__stat">{cand.totalDurationMinutes}<small>분 소요</small></span>
          <span className="rmv3-sheet__dot">·</span>
          <span className="rmv3-sheet__stat">{cand.totalTransfers}<small>회 환승</small></span>
          <span className="rmv3-sheet__dot">·</span>
          <span className="rmv3-sheet__stat">{totalWalkingMeters}<small>m 도보</small></span>
        </div>

        {busSegs.length > 0 && (
          <div className="rmv3-sheet__chips">
            {busSegs.map((seg, i) => (
              <div key={i} className="rmv3-chip">
                <span className="rmv3-chip__badge">{seg.mode === 'SUBWAY' ? '지하철' : '버스'}</span>
                <span className="rmv3-chip__line">{seg.lineName}</span>
                <span className="rmv3-chip__stop">{seg.from}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 펼친 상태: 타임라인 */}
      {expanded && (
        <div className="rmv3-sheet__expanded">
          <div className="rmv3-sheet__divider" />
          <p className="rmv3-sheet__tl-label">경로 상세</p>
          <div className="rmv3-tl">
            {timeline.map((stop, i) => (
              <div key={i} className="rmv3-tl__row">
                <div className="rmv3-tl__track">
                  <div className={`rmv3-tl__dot rmv3-tl__dot--${stop.type}`} />
                  {i < timeline.length - 1 && <div className="rmv3-tl__line" />}
                </div>
                <div className="rmv3-tl__text">
                  <p className={`rmv3-tl__name${stop.type === 'mid' ? ' rmv3-tl__name--stop' : ''}`}>
                    {stop.name}
                  </p>
                  {stop.detail && <p className="rmv3-tl__detail">{stop.detail}</p>}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ================================================================
   지도 컴포넌트
   ================================================================ */

function RouteMap({ segments }) {
  const mapRef  = useRef(null);
  const [ready, setReady] = useState(null);

  useEffect(() => {
    let cancelled = false;
    let rafId     = null;

    function initMap() {
      const kakao = window.kakao;
      if (!kakao || !kakao.maps) { setReady(false); return; }

      kakao.maps.load(() => {
        if (cancelled || !mapRef.current) return;

        const firstCoord = segments[0].path[0];
        const map = new kakao.maps.Map(mapRef.current, {
          center: new kakao.maps.LatLng(firstCoord[1], firstCoord[0]),
          level: 5,
        });

        const bounds = new kakao.maps.LatLngBounds();

        const densePaths = segments.map(seg => densifyPath(seg.path, 80));
        densePaths.forEach(path =>
          path.forEach(([lng, lat]) => bounds.extend(new kakao.maps.LatLng(lat, lng)))
        );

        // 출발 마커 (검정)
        const fp = densePaths[0][0];
        new kakao.maps.CustomOverlay({
          map, yAnchor: 1.2, xAnchor: 0.5,
          position: new kakao.maps.LatLng(fp[1], fp[0]),
          content: markerStart(),
        });

        // 도착 마커 (라벤더)
        const lp = densePaths[densePaths.length - 1];
        const ep = lp[lp.length - 1];
        new kakao.maps.CustomOverlay({
          map, yAnchor: 1.2, xAnchor: 0.5,
          position: new kakao.maps.LatLng(ep[1], ep[0]),
          content: markerEnd(),
        });

        // 환승 지점 회색 점 (첫/끝 제외)
        for (let i = 1; i < densePaths.length; i++) {
          const [lng, lat] = densePaths[i][0];
          new kakao.maps.CustomOverlay({
            map, yAnchor: 0.5, xAnchor: 0.5,
            position: new kakao.maps.LatLng(lat, lng),
            content: markerTransfer(),
          });
        }

        // 세그먼트별 폴리라인 (처음엔 빈 경로)
        const polylines = segments.map(seg =>
          new kakao.maps.Polyline({
            map,
            path: [],
            strokeWeight: seg.mode === 'WALK' ? 3 : 4,
            strokeColor: ACCENT,
            strokeOpacity: 0.88,
            strokeStyle: seg.mode === 'WALK' ? 'shortdash' : 'solid',
          })
        );

        // 이동 점
        const dotDiv = document.createElement('div');
        dotDiv.style.cssText = [
          'width:12px', 'height:12px', 'border-radius:50%',
          `background:${ACCENT}`, 'border:2.5px solid white',
          `box-shadow:0 0 10px rgba(139,123,181,0.9)`,
          'transform:translate(-50%,-50%)',
        ].join(';');
        const dotOverlay = new kakao.maps.CustomOverlay({ content: dotDiv, zIndex: 10 });

        const allDense = densePaths.flat();
        const totalPts = allDense.length;
        const DRAW_DUR = 2000;
        const MOVE_DUR = 4000;
        let startTime  = null;
        let phase      = 'drawing';

        function animate(ts) {
          if (cancelled) return;

          if (phase === 'drawing') {
            if (!startTime) startTime = ts;
            const raw    = Math.min((ts - startTime) / DRAW_DUR, 1);
            const target = Math.floor(easeInOut(raw) * totalPts);

            let drawn = 0;
            for (let si = 0; si < densePaths.length; si++) {
              if (drawn >= target) break;
              const take = Math.min(target - drawn, densePaths[si].length);
              polylines[si].setPath(
                densePaths[si].slice(0, take).map(([lng, lat]) =>
                  new kakao.maps.LatLng(lat, lng))
              );
              drawn += take;
            }

            if (raw < 1) {
              rafId = requestAnimationFrame(animate);
            } else {
              phase     = 'moving';
              startTime = null;
              dotOverlay.setMap(map);
              rafId = requestAnimationFrame(animate);
            }

          } else {
            if (!startTime) startTime = ts;
            const loopP = ((ts - startTime) % MOVE_DUR) / MOVE_DUR;
            const idx   = Math.min(Math.floor(loopP * totalPts), totalPts - 1);
            const [lng, lat] = allDense[idx];
            dotOverlay.setPosition(new kakao.maps.LatLng(lat, lng));
            rafId = requestAnimationFrame(animate);
          }
        }

        rafId = requestAnimationFrame(animate);

        if (!cancelled) {
          map.setBounds(bounds, 60, 60, 60, 60);
          setReady(true);
        }
      });
    }

    if (window.kakao && window.kakao.maps) {
      initMap();
    } else {
      const timer   = setInterval(() => {
        if (window.kakao && window.kakao.maps) { clearInterval(timer); initMap(); }
      }, 100);
      const timeout = setTimeout(() => {
        clearInterval(timer);
        if (!cancelled) setReady(false);
      }, 5000);
      return () => {
        cancelled = true;
        clearInterval(timer);
        clearTimeout(timeout);
        if (rafId) cancelAnimationFrame(rafId);
      };
    }

    return () => { cancelled = true; if (rafId) cancelAnimationFrame(rafId); };
  }, [segments]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="rmv3-map-wrap">
      <div ref={mapRef} className="rmv3-map" />

      {ready === null && (
        <div className="rmv3-map-loading">
          <div className="rmv3-map-loading__spinner" />
          <span>지도를 불러오는 중...</span>
        </div>
      )}

      {ready === false && (
        <div className="rmv3-map-error">
          <p>지도를 불러올 수 없어요</p>
          <p className="rmv3-map-error__sub">API 키 또는 네트워크를 확인해주세요</p>
        </div>
      )}
    </div>
  );
}

/* ================================================================
   메인 페이지
   ================================================================ */

export default function RouteMapV3() {
  const navigate = useNavigate();

  const schedule = mockMainData.data.nearestSchedule;
  const cand     = mockRouteData.data.candidates[0];
  const segments = cand.segments;

  const timeline = useMemo(() => buildTimeline(segments), [segments]);

  const busSegs = useMemo(
    () => segments.filter(s => s.mode === 'BUS' || s.mode === 'SUBWAY'),
    [segments],
  );

  const totalWalkingMeters = useMemo(
    () => segments
      .filter(s => s.mode === 'WALK')
      .reduce((sum, s) => sum + (s.distanceMeters ?? 0), 0),
    [segments],
  );

  return (
    <div className="rmv3">

      {/* 상단 바 */}
      <header className="rmv3-topbar">
        <button className="rmv3-topbar__back" onClick={() => navigate('/v3')}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M19 12H5M5 12l7 7M5 12l7-7"
              stroke="currentColor" strokeWidth="2"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <div className="rmv3-topbar__center">
          <span className="rmv3-topbar__title">{schedule.title}</span>
          <span className="rmv3-topbar__dest">{schedule.destination.name}</span>
        </div>
        <div className="rmv3-topbar__weather">
          <span className="rmv3-topbar__temp">18°</span>
          <span className="rmv3-topbar__cond">맑음</span>
        </div>
      </header>

      {/* 지도 */}
      <RouteMap segments={segments} />

      {/* 하단 시트 */}
      <BottomSheet
        schedule={schedule}
        cand={cand}
        timeline={timeline}
        busSegs={busSegs}
        totalWalkingMeters={totalWalkingMeters}
      />

    </div>
  );
}
