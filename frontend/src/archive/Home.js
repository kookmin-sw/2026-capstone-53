import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockRouteInfoList } from '../data/mockData';
import ScheduleCard from '../components/ScheduleCard';
import KakaoMap from '../components/KakaoMap';
import RouteCard from '../components/RouteCard';
import './Home.css';

const MY_LOCATION = { lat: 37.661,  lng: 127.012  };
const MAP_CENTER  = { lat: 37.6350, lng: 127.0035 };

export default function Home() {
  const navigate = useNavigate();
  const [activeIdx, setActiveIdx]   = useState(0);
  const [hoverSide, setHoverSide]   = useState(null); // 'prev' | 'next' | null
  const touchStartX = useRef(null);
  const total = mockRouteInfoList.length;

  const current = mockRouteInfoList[activeIdx];
  const { schedule, route, departureInfo, weather, routeStops, segments } = current;
  const stops = [route.boardingStop];

  const goNext = () => setActiveIdx(i => Math.min(i + 1, total - 1));
  const goPrev = () => setActiveIdx(i => Math.max(i - 1, 0));

  const onTouchStart = e => { touchStartX.current = e.touches[0].clientX; };
  const onTouchEnd   = e => {
    if (touchStartX.current === null) return;
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    if (dx < -50) goNext();
    else if (dx > 50) goPrev();
    touchStartX.current = null;
  };

  const onMouseMove = e => {
    if (total <= 1) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pct  = (e.clientX - rect.left) / rect.width;
    if      (pct < 0.28 && activeIdx > 0)           setHoverSide('prev');
    else if (pct > 0.72 && activeIdx < total - 1)   setHoverSide('next');
    else                                              setHoverSide(null);
  };

  return (
    <div className="home">
      <div className="home__container">

        {/* 타이틀 */}
        <div className="home__page-header">
          <div>
            <p className="home__greeting">안녕하세요 👋  오늘도 스마트하게 출발해봐요</p>
            <h1 className="home__title">오늘의 경로 안내</h1>
          </div>
          <div className="home__meta">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="9" stroke="#9CA3AF" strokeWidth="2"/>
              <path d="M12 7v5l3 3" stroke="#9CA3AF" strokeWidth="2" strokeLinecap="round"/>
            </svg>
            {departureInfo.recommendedDepartureTime} 출발 · {weather.temperature}°{' '}
            {weather.condition === 'CLEAR' ? '☀️' : weather.condition === 'RAIN' ? '🌧️' : '⛅'}
          </div>
        </div>

        {/* 메인 그리드 */}
        <div className="home__grid">
          <div className="home__left">

            {/* ── 파란 일정 카드 캐러셀 ── */}
            <div
              className="home__sch-wrap"
              onMouseMove={onMouseMove}
              onMouseLeave={() => setHoverSide(null)}
              onTouchStart={onTouchStart}
              onTouchEnd={onTouchEnd}
            >
              <div
                className="home__sch-track"
                style={{ transform: `translateX(calc(-${activeIdx} * 100%))` }}
              >
                {mockRouteInfoList.map((info, i) => (
                  <div key={i} className="home__sch-slide">
                    <ScheduleCard
                      schedule={info.schedule}
                      departureTime={info.departureInfo.recommendedDepartureTime}
                      departureMinutes={32}
                      bufferMinutes={info.departureInfo.bufferMinutes}
                      weather={info.weather}
                    />
                  </div>
                ))}
              </div>

              {/* 좌측 화살표 */}
              <button
                className={`home__nav-arrow home__nav-arrow--prev${hoverSide === 'prev' ? ' home__nav-arrow--show' : ''}`}
                onClick={goPrev}
                aria-label="이전 일정"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M15 18l-6-6 6-6" stroke="white" strokeWidth="2.5"
                    strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>

              {/* 우측 화살표 */}
              <button
                className={`home__nav-arrow home__nav-arrow--next${hoverSide === 'next' ? ' home__nav-arrow--show' : ''}`}
                onClick={goNext}
                aria-label="다음 일정"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M9 18l6-6-6-6" stroke="white" strokeWidth="2.5"
                    strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>

              {/* 인디케이터 점 */}
              {total > 1 && (
                <div className="home__dots">
                  {mockRouteInfoList.map((_, i) => (
                    <button
                      key={i}
                      className={`home__dot${i === activeIdx ? ' home__dot--on' : ''}`}
                      onClick={() => setActiveIdx(i)}
                      aria-label={`일정 ${i + 1}`}
                    />
                  ))}
                </div>
              )}
            </div>

            {/* ── 경로 안내 벤토 그리드 (자동 동기화) ── */}
            <section className="home__section">
              <div className="home__section-header">
                <h2 className="home__section-title">경로 안내</h2>
                <span className="home__schedule-badge">{schedule.title}</span>
              </div>
              <RouteCard
                route={route}
                departureInfo={departureInfo}
                routeStops={routeStops}
                segments={segments}
                onMapTap={() => navigate('/map')}
                onDepart={() => alert('출발합니다!')}
              />
            </section>

          </div>

          {/* 우측 — 지도 */}
          <div className="home__right">
            <div className="home__map-panel">
              <div className="home__section-header">
                <h2 className="home__section-title">경로 지도</h2>
              </div>
              <KakaoMap
                center={MAP_CENTER}
                myLocation={MY_LOCATION}
                stops={stops}
                segments={segments}
              />
              <p className="home__map-caption">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="9" stroke="#9CA3AF" strokeWidth="2"/>
                  <path d="M12 8v4l2 2" stroke="#9CA3AF" strokeWidth="2" strokeLinecap="round"/>
                </svg>
                내 위치 기준 · 승차 정류장까지 경로
              </p>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
