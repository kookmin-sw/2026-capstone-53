import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockRouteInfo } from '../data/mockData';
import './HomeV2.css';

const rec = mockRouteInfo;

/* ── 순차 페이드인 래퍼 ── */
function FadeIn({ delay = 0, children, className = '' }) {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setVisible(true), delay);
    return () => clearTimeout(t);
  }, [delay]);
  return (
    <div className={`hv2-fadein${visible ? ' hv2-fadein--in' : ''} ${className}`}>
      {children}
    </div>
  );
}

/* ================================================================
   HomeV2
   ================================================================ */
export default function HomeV2() {
  const navigate = useNavigate();

  const walkMins = Math.max(1, Math.round(rec.route.boardingStop.walkingTimeSeconds / 60));

  const today = new Date();
  const WD = ['일', '월', '화', '수', '목', '금', '토'];
  const dateBadge = `${today.getMonth() + 1}.${today.getDate()} ${WD[today.getDay()]}`;

  return (
    <div className="hv2-page">

      {/* ════════════════════════════════════
          헤더
          ════════════════════════════════════ */}
      <header className="hv2-header">
        <div className="hv2-header__left">
          <div className="hv2-header__avatar">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="9" fill="white" fillOpacity="0.85"/>
              <path d="M8 12h8M12 8v8" stroke="#2563EB" strokeWidth="2.2" strokeLinecap="round"/>
            </svg>
          </div>
          <div>
            <div className="hv2-header__name">경로 안내</div>
            <div className="hv2-header__status">
              <span className="hv2-header__dot" />
              경로 계산 완료
            </div>
          </div>
        </div>
        <div className="hv2-date-badge">{dateBadge}</div>
      </header>

      {/* ════════════════════════════════════
          대화 영역
          ════════════════════════════════════ */}
      <div className="hv2-chat">

        {/* 1 — 일정 안내 */}
        <FadeIn delay={200}>
          <div className="hv2-row">
            <div className="hv2-mini-avatar" />
            <div className="hv2-bubble">
              오늘 {rec.schedule.arrivalTime} {rec.schedule.title} 일정이 있네요.
            </div>
          </div>
        </FadeIn>

        {/* 2 — 버스 안내 */}
        <FadeIn delay={520}>
          <div className="hv2-bubble hv2-bubble--cont">
            {rec.route.boardingStop.stopName} 정류장에서{' '}
            <strong className="hv2-blue">{rec.route.busRoute.routeName}번 버스</strong>를 타면 돼요.
          </div>
        </FadeIn>

        {/* 3 — 경로 요약 카드 */}
        <FadeIn delay={920} className="hv2-indent">
          <div className="hv2-route-card">
            <div className="hv2-route-card__head">
              <span className="hv2-badge-blue">경로 안내</span>
              <span className="hv2-route-card__dist">
                도보 {walkMins}분 거리
              </span>
            </div>

            <div className="hv2-route-card__body">
              <div className="hv2-route-card__info">
                <div className="hv2-route-card__stop">{rec.route.boardingStop.stopName}</div>
                <div className="hv2-route-card__bus">
                  {rec.route.busRoute.routeName}번 · {rec.route.busRoute.busArrivalMinutes}분 뒤 도착
                </div>
                <div className="hv2-pills">
                  <span className="hv2-pill">🚶 도보 {walkMins}분</span>
                  <span className="hv2-pill">🚌 버스 {rec.route.busRoute.busTripMinutes}분</span>
                  <span className="hv2-pill hv2-pill--blue">총 {rec.route.totalTripMinutes}분</span>
                  <span className="hv2-pill">
                    환승 {rec.route.transferCount === 0 ? '없음' : `${rec.route.transferCount}회`}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </FadeIn>

        {/* 4 — 출발 시간 말풍선 */}
        <FadeIn delay={1320}>
          <div className="hv2-row">
            <div className="hv2-mini-avatar" />
            <div className="hv2-bubble">
              <div>
                {rec.departureInfo.recommendedDepartureTime}에 출발하면 여유있게 도착해요.
              </div>
              <div className="hv2-bubble__sub">여유시간 {rec.departureInfo.bufferMinutes}분 포함</div>
            </div>
          </div>
        </FadeIn>

        {/* 5 — 노선 흐름 카드 */}
        <FadeIn delay={1680} className="hv2-indent">
          <div className="hv2-info-card">
            <div className="hv2-info-card__label">노선 흐름</div>
            <div className="hv2-flow-scroll">
              {rec.routeStops.slice(0, 4).map((stop, i, arr) => (
                <React.Fragment key={stop.stopName}>
                  <span className={`hv2-stop-chip${stop.type === 'BOARDING' ? ' hv2-stop-chip--boarding' : ''}`}>
                    {stop.stopName}
                  </span>
                  {i < arr.length - 1 && (
                    <svg className="hv2-arrow" width="13" height="13" viewBox="0 0 24 24" fill="none">
                      <path d="M5 12h14M13 6l6 6-6 6"
                        stroke="#C5C0B8" strokeWidth="2.5"
                        strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  )}
                </React.Fragment>
              ))}
            </div>
          </div>
        </FadeIn>

        {/* 6 — CTA 말풍선 */}
        <FadeIn delay={2040}>
          <div className="hv2-row">
            <div className="hv2-mini-avatar" />
            <div className="hv2-bubble">
              {rec.departureInfo.recommendedDepartureTime}에 출발할까요?
            </div>
          </div>
        </FadeIn>

        <div className="hv2-chat-spacer" />
      </div>

      {/* ════════════════════════════════════
          하단 푸터
          ════════════════════════════════════ */}
      <footer className="hv2-footer">
        <div className="hv2-actions">
          <button className="hv2-btn-primary" onClick={() => alert('출발합니다!')}>
            이 경로로 출발
          </button>
          <button className="hv2-btn-map" onClick={() => navigate('/')}>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none">
              <path d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3M9 20V7m6 13l4.553 2.276A1 1 0 0021 21.382V10.618a1 1 0 00-.553-.894L15 7m0 13V7M9 7l6-3"
                stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            지도
          </button>
        </div>
        <div className="hv2-actions__meta">
          {rec.weather.temperature}°{' '}
          {rec.weather.condition === 'CLEAR' ? '☀️ 맑음' : rec.weather.condition === 'RAIN' ? '🌧️ 비' : '⛅ 흐림'}
          {' '}· 총 {rec.route.totalTripMinutes}분 소요
        </div>

        <nav className="hv2-nav">
          <button className="hv2-nav__tab hv2-nav__tab--active" onClick={() => navigate('/v2')}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
                stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <span>경로</span>
          </button>
          <button className="hv2-nav__tab" onClick={() => navigate('/v2/calendar')}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
              <path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
            <span>캘린더</span>
          </button>
          <button className="hv2-nav__tab" onClick={() => navigate('/settings')}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2"/>
              <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"
                stroke="currentColor" strokeWidth="2"/>
            </svg>
            <span>설정</span>
          </button>
        </nav>
      </footer>

    </div>
  );
}
