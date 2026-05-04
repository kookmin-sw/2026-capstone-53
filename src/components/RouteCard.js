import React, { useRef, useEffect } from 'react';
import { useTheme } from '../contexts/ThemeContext';
import './RouteCard.css';

function usePointColor() {
  const { theme } = useTheme();
  return theme === 'dark' ? '#8BB5E0' : '#2563EB';
}

function MiniMapCell({ segments, onMapTap }) {
  const mapRef  = useRef(null);
  const mapInst = useRef(null);
  const pointColor = usePointColor();

  useEffect(() => {
    if (!mapRef.current || !window.kakao?.maps || !segments?.length) return;

    // 이전 지도 인스턴스 제거 후 재초기화
    mapRef.current.innerHTML = '';
    mapInst.current = null;

    window.kakao.maps.load(() => {
      if (!mapRef.current) return;
      const { maps } = window.kakao;

      const allPoints = segments.flatMap(seg =>
        seg.path.map(([lng, lat]) => new maps.LatLng(lat, lng))
      );
      if (allPoints.length < 2) return;

      const map = new maps.Map(mapRef.current, {
        center: allPoints[0],
        level: 5,
        draggable: false,
        scrollwheel: false,
        disableDoubleClickZoom: true,
      });
      map.setDraggable(false);
      map.setZoomable(false);
      mapInst.current = map;

      new maps.Polyline({
        map,
        path: allPoints,
        strokeWeight: 3,
        strokeColor: pointColor,
        strokeOpacity: 0.9,
        strokeStyle: 'solid',
      });

      const makeDot = (pos, color) => new maps.CustomOverlay({
        map,
        position: pos,
        content: `<div style="width:10px;height:10px;border-radius:50%;background:${color};border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,.3)"></div>`,
        xAnchor: 0.5,
        yAnchor: 0.5,
        zIndex: 3,
      });
      makeDot(allPoints[0], '#22C55E');
      makeDot(allPoints[allPoints.length - 1], '#EF4444');

      const bounds = new maps.LatLngBounds();
      allPoints.forEach(p => bounds.extend(p));
      map.setBounds(bounds, 40);
    });

    return () => {
      if (mapRef.current) mapRef.current.innerHTML = '';
      mapInst.current = null;
    };
  }, [segments]);

  return (
    <div className="rc-cell rc-cell--map" onClick={onMapTap}>
      <div ref={mapRef} style={{ width: '100%', height: '100%' }} />
      <div className="rc-map-overlay">
        <span className="rc-map-badge">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
            <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="currentColor"/>
            <circle cx="12" cy="9" r="2.5" fill="white"/>
          </svg>
          지도 보기
        </span>
      </div>
    </div>
  );
}

export default function RouteCard({ route, departureInfo, routeStops, segments, onDepart, onMapTap }) {
  const { boardingStop, busRoute, totalTripMinutes, transferCount } = route;
  const walkMins = Math.max(1, Math.round(boardingStop.walkingTimeSeconds / 60));
  const pointColor = usePointColor();

  return (
    <div className="rc-bento">

      {/* ── A: 승차 정류장 (col 1-2) ── */}
      <div className="rc-cell rc-cell--stop">
        <span className="rc-label">승차 정류장</span>
        <span className="rc-stop-name">{boardingStop.stopName}</span>
        <span className="rc-walk-badge">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="5" r="2" fill="currentColor"/>
            <path d="M9 9l-2 5h3l1 5M15 9l2 5h-3l-1 5M9 9h6"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          도보 {walkMins}분
        </span>
      </div>

      {/* ── B: 총 소요시간 (col 3, blue) ── */}
      <div className="rc-cell rc-cell--total">
        <span className="rc-label">총 소요</span>
        <span className="rc-big-num">{totalTripMinutes}</span>
        <span className="rc-unit">분</span>
      </div>

      {/* ── C: 권장 출발 시간 (col 1-2, tall) ── */}
      <div className="rc-cell rc-cell--depart">
        <span className="rc-label">권장 출발</span>
        <span className="rc-depart-time">{departureInfo.recommendedDepartureTime}</span>
        <div className="rc-depart-row">
          <svg width="18" height="10" viewBox="0 0 18 10" fill="none">
            <path d="M1 5H15M11 1l4 4-4 4" stroke={pointColor} strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="rc-arrive-time">09:00 도착</span>
        </div>
        {departureInfo.bufferMinutes != null && (
          <span className="rc-buffer">여유 {departureInfo.bufferMinutes}분 포함</span>
        )}
      </div>

      {/* ── D: 버스 + 환승 (col 3, compact) ── */}
      <div className="rc-cell rc-cell--bus">
        <span className="rc-label">버스</span>
        <span className="rc-bus-num">{busRoute.routeName}</span>
        <span className="rc-arrival">
          <span className="rc-pulse" />
          {busRoute.busArrivalMinutes}분 후
        </span>
        <div className="rc-transfer-chip">
          환승 {transferCount === 0 ? '없음' : `${transferCount}회`}
        </div>
      </div>

      {/* ── E: 미니맵 (col 1-3) ── */}
      {segments?.length > 0 && (
        <MiniMapCell segments={segments} onMapTap={onMapTap} />
      )}

      {/* ── F: 구간별 소요 타임라인 (col 1-3) ── */}
      <div className="rc-cell rc-cell--timeline">
        <span className="rc-label">구간별 소요</span>
        <div className="rc-tl-row">
          <div className="rc-tl-item">
            <div className="rc-tl-icon rc-tl-icon--walk">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="5" r="2" fill="currentColor"/>
                <path d="M9 9l-2 5h3l1 5M15 9l2 5h-3l-1 5M9 9h6"
                  stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
            <span className="rc-tl-val">{walkMins}분</span>
            <span className="rc-tl-sub">도보</span>
          </div>

          <svg className="rc-tl-arrow" width="16" height="10" viewBox="0 0 16 10" fill="none">
            <path d="M1 5H13M9 1l4 4-4 4" stroke="#C5BFB8" strokeWidth="1.5"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>

          <div className="rc-tl-item">
            <div className="rc-tl-icon rc-tl-icon--bus">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                <rect x="2" y="5" width="20" height="14" rx="3" fill="currentColor" fillOpacity="0.15"/>
                <rect x="2" y="5" width="20" height="14" rx="3" stroke="currentColor" strokeWidth="1.8"/>
                <path d="M2 10H22M7 19V21M17 19V21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
              </svg>
            </div>
            <span className="rc-tl-val">{busRoute.busTripMinutes}분</span>
            <span className="rc-tl-sub">버스</span>
          </div>

          <span className="rc-tl-eq">=</span>

          <div className="rc-tl-item rc-tl-item--total">
            <span className="rc-tl-val rc-tl-val--blue">총 {totalTripMinutes}분</span>
            <span className="rc-tl-sub">소요</span>
          </div>
        </div>
      </div>

      {/* ── G: 노선 흐름 (col 1-3) ── */}
      {routeStops && routeStops.length > 0 && (
        <div className="rc-cell rc-cell--flow">
          <span className="rc-label">노선 흐름</span>
          <div className="rc-flow-scroll">
            {routeStops.map((stop, i, arr) => (
              <React.Fragment key={stop.stopName}>
                <span className={`rc-flow-chip${stop.type === 'BOARDING' ? ' rc-flow-chip--on' : ''}`}>
                  {stop.stopName}
                </span>
                {i < arr.length - 1 && (
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                    <path d="M5 12h14M13 6l6 6-6 6" stroke="#C5BFB8" strokeWidth="2.5"
                      strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </React.Fragment>
            ))}
          </div>
        </div>
      )}

      {/* ── H: CTA ── */}
      {onDepart && (
        <button className="rc-cta" onClick={onDepart}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M5 12h14M12 5l7 7-7 7" stroke="white" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          이 경로로 출발하기
        </button>
      )}
    </div>
  );
}
