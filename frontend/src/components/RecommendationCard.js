import React from 'react';
import './RecommendationCard.css';

const CONGESTION_MAP = {
  LOW:    { label: '쾌적', color: '#15803D', iconBg: '#D1FAE5' },
  MEDIUM: { label: '보통', color: '#B45309', iconBg: '#FEF3C7' },
  HIGH:   { label: '혼잡', color: '#B91C1C', iconBg: '#FEE2E2' },
};

const ROUTE_STOP_STYLE = {
  RECOMMENDED: { color: '#15803D', bg: '#D1FAE5', border: '#34C759', fontWeight: 700 },
  BASELINE:    { color: '#DC2626', bg: '#FEE2E2', border: '#EF4444', fontWeight: 600 },
  NORMAL:      { color: '#6B7280', bg: '#F3F0EC', border: '#E5DDD5', fontWeight: 500 },
};

function formatWalkMins(seconds) {
  return `${Math.round(seconds / 60)}분`;
}

/* ── 노선 정류장 순서 ── */
function RouteFlow({ routeStops }) {
  if (!routeStops || routeStops.length === 0) return null;
  return (
    <div className="route-flow">
      <div className="route-flow__label">
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
          <rect x="2" y="5" width="20" height="14" rx="3" stroke="#C0BAB4" strokeWidth="2"/>
          <path d="M2 10H22M7 18V20M17 18V20" stroke="#C0BAB4" strokeWidth="2" strokeLinecap="round"/>
        </svg>
        노선 정류장 순서
      </div>
      <div className="route-flow__scroll">
        {routeStops.map((stop, i) => {
          const s = ROUTE_STOP_STYLE[stop.type] || ROUTE_STOP_STYLE.NORMAL;
          return (
            <React.Fragment key={stop.stopName}>
              <div
                className="route-flow__stop"
                style={{ color: s.color, background: s.bg, border: `1.5px solid ${s.border}`, fontWeight: s.fontWeight }}
              >
                {stop.type === 'RECOMMENDED' && <span className="route-flow__stop-icon">★</span>}
                {stop.type === 'BASELINE'    && <span className="route-flow__stop-icon">●</span>}
                {stop.stopName}
              </div>
              {i < routeStops.length - 1 && (
                <div className="route-flow__connector">
                  <svg width="14" height="10" viewBox="0 0 14 10" fill="none">
                    <path d="M1 5H11M11 5L7 1M11 5L7 9" stroke="#C8BFB5" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
              )}
            </React.Fragment>
          );
        })}
      </div>
      <div className="route-flow__legend">
        <span className="route-flow__legend-item route-flow__legend-item--rec">★ 추천</span>
        <span className="route-flow__legend-item route-flow__legend-item--base">● 기본</span>
      </div>
    </div>
  );
}

/* ── 메인 추천 카드 ── */
function PrimaryCard({ stop, improvement, routeStops }) {
  const congestion  = CONGESTION_MAP[stop.congestionLevel] || CONGESTION_MAP.LOW;
  const walkMins    = formatWalkMins(stop.walkingTimeSeconds);
  const seatPct     = Math.round(stop.seatProbability * 100);
  const circumf     = 2 * Math.PI * 38;

  return (
    <div className="bento-card">

      {/* 배너 */}
      <div className="bento-banner">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <path d="M13 7L18 12L13 17M6 12H18" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        기본 대비 앉아갈 확률 <strong>{improvement.seatProbabilityDelta}</strong>
        &nbsp;· 도보 {formatWalkMins(improvement.additionalWalkingSeconds)} 추가
      </div>

      {/* 추천 정류장 */}
      <div className="bento-cell bento-stop-cell">
        <span className="bento-tag">★ 추천 정류장</span>
        <h2 className="bento-stop-name">{stop.stopName}</h2>
        {stop.walkingDistanceMeters != null && (
          <div className="bento-distance-pill">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
              <path d="M12 2C8.686 2 6 4.686 6 8c0 4.5 6 12 6 12s6-7.5 6-12c0-3.314-2.686-6-6-6z"
                stroke="#15803D" strokeWidth="2" fill="none"/>
              <circle cx="12" cy="8" r="2.2" fill="#15803D"/>
            </svg>
            {stop.walkingDistanceMeters}m
          </div>
        )}
      </div>

      {/* 앉아갈 확률 — 2행 병합 */}
      <div className="bento-cell bento-prob-cell">
        <span className="bento-cell-label">앉아갈 확률</span>
        <div className="bento-prob-ring">
          <svg width="96" height="96" viewBox="0 0 96 96">
            <circle cx="48" cy="48" r="38" fill="none" stroke="#D1FAE5" strokeWidth="9"/>
            <circle
              className="bento-ring-progress"
              cx="48" cy="48" r="38"
              fill="none"
              stroke="#34C759"
              strokeWidth="9"
              strokeLinecap="round"
              strokeDasharray={circumf}
              style={{
                '--ring-full':   circumf,
                '--ring-target': circumf * (1 - stop.seatProbability),
              }}
              transform="rotate(-90 48 48)"
            />
          </svg>
          <div className="bento-prob-text">
            <span className="bento-prob-num">
              {seatPct}<span className="bento-prob-unit">%</span>
            </span>
          </div>
        </div>
        <span className="bento-prob-label">착석 가능 확률</span>
      </div>

      {/* 버스 노선 */}
      <div className="bento-cell bento-bus-cell">
        <span className="bento-cell-label">버스 노선</span>
        <div className="bento-bus-numrow">
          <span className="bento-bus-num">{stop.busRouteName}</span>
          <span className="bento-bus-suffix">번</span>
        </div>
        <div className="bento-arrival-chip">
          <span className="bento-arrival-pulse" />
          {stop.busArrivalMinutes}분 후 도착
        </div>
      </div>

      {/* 도보 · 혼잡도 · 총 소요 */}
      <div className="bento-stats-row">
        <div className="bento-cell bento-stat">
          <div className="bento-stat-icon" style={{ background: '#E8F3FF' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="5" r="2" fill="#0A7AFF"/>
              <path d="M9 9l-2 5h3l1 5M15 9l2 5h-3l-1 5M9 9h6"
                stroke="#0A7AFF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="bento-stat-value">{walkMins}</span>
          <span className="bento-stat-sublabel">도보</span>
        </div>

        <div className="bento-cell bento-stat">
          <div className="bento-stat-icon" style={{ background: congestion.iconBg }}>
            <svg width="14" height="14" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="7" fill={congestion.color} opacity="0.85"/>
            </svg>
          </div>
          <span className="bento-stat-value" style={{ color: congestion.color }}>
            {congestion.label}
          </span>
          <span className="bento-stat-sublabel">혼잡도</span>
        </div>

        <div className="bento-cell bento-stat">
          <div className="bento-stat-icon" style={{ background: '#F0EBE3' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="9" stroke="#6E6E73" strokeWidth="2"/>
              <path d="M12 7V12L15 15" stroke="#6E6E73" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </div>
          <span className="bento-stat-value">{stop.totalTripMinutes}분</span>
          <span className="bento-stat-sublabel">총 소요</span>
        </div>
      </div>

      {/* 노선 정류장 순서 */}
      {routeStops && routeStops.length > 0 && (
        <div className="bento-cell bento-route-cell">
          <RouteFlow routeStops={routeStops} />
        </div>
      )}

    </div>
  );
}

/* ── 기본 경로 카드 ── */
function BaselineCard({ stop }) {
  const seatPct = Math.round(stop.seatProbability * 100);
  return (
    <div className="bento-baseline">
      <div className="bento-baseline__header">
        <div className="bento-baseline__name-group">
          <span className="bento-baseline__tag">기본 경로</span>
          <span className="bento-baseline__name">{stop.stopName}</span>
          {stop.walkingDistanceMeters != null && (
            <span className="bento-baseline__dist">내 위치에서 {stop.walkingDistanceMeters}m</span>
          )}
        </div>
      </div>
      <div className="bento-baseline__stats">
        <span>앉아갈 확률 <strong style={{ color: '#6E6E73' }}>{seatPct}%</strong></span>
        <div className="bento-baseline__divider" />
        <span>총 {stop.totalTripMinutes}분</span>
      </div>
    </div>
  );
}

function RecommendationCard({ primaryStop, baselineStop, improvement, routeStops }) {
  return (
    <div className="bento-section">
      <PrimaryCard stop={primaryStop} improvement={improvement} routeStops={routeStops} />
      <BaselineCard stop={baselineStop} />
    </div>
  );
}

export default RecommendationCard;
