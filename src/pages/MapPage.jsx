import React, { useRef, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockRouteData } from '../data/mockData';
import { useTheme } from '../contexts/ThemeContext';
import './MapPage.css';

const SEGMENTS = mockRouteData.data.candidates[0].segments;

export default function MapPage() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const pointColor = '#2563EB';
  const mapRef   = useRef(null);
  const sheetRef = useRef(null);

  const [expanded, setExpanded] = useState(false);
  const [dragging, setDragging] = useState(false);
  const dragStartY  = useRef(null);
  const dragStartH  = useRef(null);
  const sheetHeight = useRef(108);

  const COLLAPSED = 108;
  const EXPANDED  = 384;

  /* ── 지도 초기화 ── */
  useEffect(() => {
    if (!mapRef.current || !window.kakao?.maps) return;

    window.kakao.maps.load(() => {
      const { maps } = window.kakao;

      const allPoints = SEGMENTS.flatMap(seg =>
        seg.path.map(([lng, lat]) => new maps.LatLng(lat, lng))
      );

      const map = new maps.Map(mapRef.current, {
        center: allPoints[0],
        level: 5,
      });

      /* 폴리라인 */
      SEGMENTS.forEach(seg => {
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
  }, []);

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
  const stops = [
    { label: SEGMENTS[0].from, type: 'origin' },
    ...SEGMENTS.map(seg => ({ label: seg.to, mode: seg.mode, line: seg.lineName })),
  ];

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
        <span className="mp-topbar-dur">약 30분</span>
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
        <div className="mp-summary">
          <span className="mp-summary-from">{SEGMENTS[0].from}</span>
          <svg width="20" height="10" viewBox="0 0 20 10" fill="none">
            <path d="M1 5H17M13 1l4 4-4 4" stroke="#C5BFB8" strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="mp-summary-to">{SEGMENTS[SEGMENTS.length - 1].to}</span>
        </div>

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
