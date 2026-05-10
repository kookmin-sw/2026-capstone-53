import React, { useRef, useEffect, useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { api } from '../api';
import { ErrorState } from '../components/StateUI';
import './MapPage.css';

export default function MapPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme } = useTheme();
  const pointColor = '#2563EB';
  const mapRef   = useRef(null);
  const sheetRef = useRef(null);

  const [uiState, setUiState]   = useState('loading');
  const [routeData, setRouteData] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const [dragging, setDragging] = useState(false);
  const dragStartY  = useRef(null);
  const dragStartH  = useRef(null);
  const sheetHeight = useRef(108);

  const COLLAPSED = 108;
  const EXPANDED  = 384;

  const scheduleId = searchParams.get('scheduleId');

  /* ── 경로 데이터 로드 ── */
  const fetchRoute = useCallback(async () => {
    if (!scheduleId) {
      setUiState('error');
      return;
    }
    setUiState('loading');
    try {
      const data = await api.route.get(scheduleId);
      setRouteData(data);
      setUiState('ready');
    } catch (err) {
      console.error('[MapPage] 경로 로드 실패', err);
      setUiState('error');
    }
  }, [scheduleId]);

  useEffect(() => {
    fetchRoute();
  }, [fetchRoute]);

  const segments = routeData?.route?.segments ?? [];

  /* ── 지도 초기화 ── */
  useEffect(() => {
    if (uiState !== 'ready' || !mapRef.current || !window.kakao?.maps || segments.length === 0) return;

    window.kakao.maps.load(() => {
      const { maps } = window.kakao;

      const allPoints = segments.flatMap(seg =>
        seg.path.map(([lng, lat]) => new maps.LatLng(lat, lng))
      );

      const map = new maps.Map(mapRef.current, {
        center: allPoints[0],
        level: 5,
      });

      /* 폴리라인 */
      segments.forEach(seg => {
        const path = seg.path.map(([lng, lat]) => new maps.LatLng(lat, lng));
        new maps.Polyline({
          map,
          path,
          strokeWeight: seg.mode === 'WALK' ? 3 : 5,
          strokeColor: seg.mode === 'WALK' ? '#6B7280' : pointColor,
          strokeOpacity: 0.9,
          strokeStyle: seg.mode === 'WALK' ? 'shortdot' : 'solid',
        });
      });

      /* 시작/종료 마커 */
      const makeDot = (pos, color) => new maps.CustomOverlay({
        map,
        position: pos,
        content: `<div style="width:12px;height:12px;border-radius:50%;background:${color};border:2.5px solid white;box-shadow:0 1px 6px rgba(0,0,0,.35)"></div>`,
        xAnchor: 0.5,
        yAnchor: 0.5,
        zIndex: 3,
      });
      makeDot(allPoints[0], '#22C55E');
      makeDot(allPoints[allPoints.length - 1], '#EF4444');

      /* fitBounds */
      const bounds = new maps.LatLngBounds();
      allPoints.forEach(p => bounds.extend(p));
      map.setBounds(bounds, 60);
    });
  }, [uiState, segments, pointColor]);

  /* ── 바텀시트 드래그 ── */
  const onPointerDown = e => {
    setDragging(true);
    dragStartY.current = e.clientY ?? e.touches?.[0]?.clientY;
    dragStartH.current = sheetHeight.current;
    sheetRef.current?.setPointerCapture?.(e.pointerId);
  };

  const onPointerMove = e => {
    if (!dragging) return;
    const y   = e.clientY ?? e.touches?.[0]?.clientY;
    const dy  = dragStartY.current - y;
    const raw = Math.max(COLLAPSED, Math.min(EXPANDED, dragStartH.current + dy));
    sheetHeight.current = raw;
    if (sheetRef.current) sheetRef.current.style.height = raw + 'px';
  };

  const onPointerUp = () => {
    if (!dragging) return;
    setDragging(false);
    const snap = sheetHeight.current > (COLLAPSED + EXPANDED) / 2;
    setExpanded(snap);
    sheetHeight.current = snap ? EXPANDED : COLLAPSED;
    if (sheetRef.current) sheetRef.current.style.height = '';
  };

  /* ── 경로 구간 목록 ── */
  const stops = segments.length > 0
    ? [
        { label: segments[0].from, type: 'origin' },
        ...segments.map(seg => ({ label: seg.to, mode: seg.mode, line: seg.lineName })),
      ]
    : [];

  const totalMin = routeData?.route?.totalDurationMinutes;

  /* ── 로딩 / 에러 ── */
  if (uiState === 'loading') {
    return (
      <div className="mp">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
          <div style={{
            width: 32, height: 32, border: '3px solid #EAE2D8',
            borderTopColor: 'var(--color-point)', borderRadius: '50%',
            animation: 'spin 0.7s linear infinite',
          }} />
        </div>
      </div>
    );
  }

  if (uiState === 'error') {
    return (
      <div className="mp">
        <div className="mp-topbar">
          <button className="mp-back" onClick={() => navigate(-1)} aria-label="뒤로">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M15 18l-6-6 6-6" stroke="#1C1C1E" strokeWidth="2.2"
                strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
          <span className="mp-topbar-title">경로 지도</span>
        </div>
        <div style={{ paddingTop: 80 }}>
          <ErrorState onRetry={() => fetchRoute()} />
        </div>
      </div>
    );
  }

  return (
    <div className="mp">
      {/* 지도 */}
      <div ref={mapRef} className="mp-map" />

      {/* 상단 바 */}
      <div className="mp-topbar">
        <button className="mp-back" onClick={() => navigate(-1)} aria-label="뒤로">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M15 18l-6-6 6-6" stroke="#1C1C1E" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <span className="mp-topbar-title">경로 지도</span>
        {totalMin && <span className="mp-topbar-dur">약 {totalMin}분</span>}
      </div>

      {/* 바텀시트 */}
      <div
        ref={sheetRef}
        className={`mp-sheet${expanded ? ' mp-sheet--open' : ''}`}
        style={{ height: expanded ? EXPANDED : COLLAPSED }}
      >
        {/* 드래그 핸들 */}
        <div
          className="mp-handle-area"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
        >
          <div className="mp-handle" />
        </div>

        {/* 요약 행 */}
        {segments.length > 0 && (
          <div className="mp-summary">
            <span className="mp-summary-from">{segments[0].from}</span>
            <svg width="20" height="10" viewBox="0 0 20 10" fill="none">
              <path d="M1 5H17M13 1l4 4-4 4" stroke="#C5BFB8" strokeWidth="1.8"
                strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <span className="mp-summary-to">{segments[segments.length - 1].to}</span>
          </div>
        )}

        {/* 상세 타임라인 (펼쳐졌을 때) */}
        <div className="mp-timeline">
          {stops.map((s, i) => (
            <div key={i} className="mp-tl-row">
              <div className={`mp-tl-dot${s.type === 'origin' ? ' mp-tl-dot--start' : i === stops.length - 1 ? ' mp-tl-dot--end' : ''}`} />
              <div className="mp-tl-info">
                <span className="mp-tl-name">{s.label}</span>
                {s.mode && (
                  <span className="mp-tl-mode">{s.mode === 'WALK' ? '도보' : s.line}</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
