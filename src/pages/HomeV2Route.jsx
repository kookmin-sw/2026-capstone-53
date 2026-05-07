import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { mockRouteInfoList, mockMember } from '../data/mockData';
import { useTheme } from '../contexts/ThemeContext';
import { useSettings } from '../contexts/SettingsContext';
import ScheduleCard from '../components/ScheduleCard';
import KakaoMap from '../components/KakaoMap';
import { HomeSkeletons, HomeEmpty, ErrorState } from '../components/StateUI';
import './HomeV2Route.css';
import '../components/RouteCard.css';

function calcReminderTime(departureTime, offsetMinutes) {
  const [h, m] = departureTime.split(':').map(Number);
  const total = h * 60 + m - offsetMinutes;
  const rh = Math.floor(((total % 1440) + 1440) % 1440 / 60);
  const rm = ((total % 1440) + 1440) % 1440 % 60;
  return `${String(rh).padStart(2, '0')}:${String(rm).padStart(2, '0')}`;
}

function getGreeting() {
  const h = new Date().getHours();
  if (h >= 5  && h <= 8)  return '좋은 아침이에요';
  if (h >= 9  && h <= 11) return '오전도 힘내요';
  if (h >= 12 && h <= 13) return '잠깐 쉬어가세요';
  if (h >= 14 && h <= 17) return '오후도 함께할게요';
  if (h >= 18 && h <= 20) return '좋은 저녁이에요';
  return '늦은 시간이네요';
}

const MY_LOCATION = { lat: 37.661, lng: 127.012 };

export default function HomeNoMap() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme } = useTheme();
  const pointColor = theme === 'dark' ? '#8BB5E0' : '#2563EB';
  const { settings } = useSettings();

  const [uiState, setUiState] = useState('loading');
  const [activeIdx, setActiveIdx] = useState(0);
  const [hoverSide, setHoverSide] = useState(null);
  const [busExpanded, setBusExpanded] = useState(false);
  const touchStartX = useRef(null);
  const total = mockRouteInfoList.length;

  useEffect(() => {
    const forced = searchParams.get('state');
    if (forced === 'loading') return;
    if (forced === 'error')   { setUiState('error');  return; }
    if (forced === 'empty')   { setUiState('empty');  return; }
    const t = setTimeout(() => setUiState('ready'), 1000);
    return () => clearTimeout(t);
  }, [searchParams]);

  const retry = () => {
    setUiState('loading');
    setTimeout(() => setUiState('ready'), 1000);
  };

  const current = mockRouteInfoList[activeIdx];
  const { schedule, route, departureInfo, weather, segments, routeStops } = current;
  const { boardingStop, busRoute, totalTripMinutes, transferCount } = route;
  const walkMins = Math.max(1, Math.round(boardingStop.walkingTimeSeconds / 60));
  const stops = [route.boardingStop];

  const mapCenter = segments?.length
    ? (() => {
        const allPts = segments.flatMap(s => s.path);
        const lats = allPts.map(([, lat]) => lat);
        const lngs = allPts.map(([lng]) => lng);
        return {
          lat: (Math.min(...lats) + Math.max(...lats)) / 2,
          lng: (Math.min(...lngs) + Math.max(...lngs)) / 2,
        };
      })()
    : MY_LOCATION;

  const goNext = () => setActiveIdx(i => Math.min(i + 1, total - 1));
  const goPrev = () => setActiveIdx(i => Math.max(i - 1, 0));

  const onTouchStart = e => { touchStartX.current = e.touches[0].clientX; };
  const onTouchEnd = e => {
    if (touchStartX.current === null) return;
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    if (dx < -50) goNext();
    else if (dx > 50) goPrev();
    touchStartX.current = null;
  };

  const onMouseMove = e => {
    if (total <= 1) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    if      (pct < 0.28 && activeIdx > 0)         setHoverSide('prev');
    else if (pct > 0.72 && activeIdx < total - 1) setHoverSide('next');
    else                                            setHoverSide(null);
  };

  return (
    <div className="home home--no-map">
      <div className="home__container hnm__container">

        {/* 타이틀 */}
        <div className="home__page-header">
          <div>
            <p className="home__greeting">
              {getGreeting()}{mockMember?.data?.nickname ? `, ${mockMember.data.nickname}님` : ''}
            </p>
            <h1 className="home__title">오늘의 경로 안내</h1>
          </div>
        </div>

        {/* 상태별 렌더링 */}
        {uiState === 'loading' && <HomeSkeletons />}
        {uiState === 'error'   && <ErrorState onRetry={retry} />}
        {uiState === 'empty'   && <HomeEmpty onCalendar={() => navigate('/calendar')} />}

        {uiState === 'ready' && (
          <div className="hnm__grid">

            {/* 왼쪽 — 일정 카드 + 벤토 */}
            <div className="hnm__info-col">

              {/* 일정 카드 캐러셀 */}
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

              {/* 경로 안내 벤토 4칸 */}
              <section className="home__section">
                <div className="home__section-header">
                  <h2 className="home__section-title">경로 안내</h2>
                  <span className="home__schedule-badge">{schedule.title}</span>
                </div>

                <div className="rc-bento hnm-bento">

                  {/* A: 승차 정류장 (col 1-2) */}
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

                  {/* B: 총 소요시간 (col 3, blue) */}
                  <div className="rc-cell rc-cell--total">
                    <span className="rc-label">총 소요</span>
                    <span className="rc-big-num">{totalTripMinutes}</span>
                    <span className="rc-unit">분</span>
                  </div>

                  {/* C: 권장 출발 시간 (col 1-2) */}
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

                    {settings.alertEnabled && (
                      <span className="rc-reminder">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"
                            stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        {calcReminderTime(departureInfo.recommendedDepartureTime, settings.alertBufferMinutes)} 알림 예정
                      </span>
                    )}
                  </div>

                  {/* D: 버스 + 환승 (col 3) — 클릭 시 상세 펼침 */}
                  <div
                    className={`rc-cell rc-cell--bus hnm-bus-cell${busExpanded ? ' hnm-bus-cell--on' : ''}`}
                    onClick={() => setBusExpanded(v => !v)}
                    role="button"
                    aria-expanded={busExpanded}
                  >
                    <div className="hnm-bus-cell__top">
                      <span className="rc-label">버스</span>
                      <svg
                        className={`hnm-bus-chevron${busExpanded ? ' hnm-bus-chevron--up' : ''}`}
                        width="14" height="14" viewBox="0 0 24 24" fill="none"
                      >
                        <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2.2"
                          strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    </div>
                    <span className="rc-bus-num">{busRoute.routeName}</span>
                    <span className="rc-arrival">
                      <span className="rc-pulse" />
                      {busRoute.busArrivalMinutes}분 후
                    </span>
                    <div className="rc-transfer-chip">
                      환승 {transferCount === 0 ? '없음' : `${transferCount}회`}
                    </div>
                  </div>

                  {/* E: 구간별 소요 타임라인 — 펼침 시 표시 */}
                  {busExpanded && (
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
                  )}

                  {/* F: 노선 흐름 — 펼침 시 표시 */}
                  {busExpanded && routeStops?.length > 0 && (
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

                </div>
              </section>

            </div>{/* hnm__info-col */}

            {/* 오른쪽 — 큰 지도 */}
            <div className="hnm__map-col">
              <div className="home__map-panel hnm-map-panel">
                <div className="home__section-header">
                  <h2 className="home__section-title">경로 지도</h2>
                </div>
                <KakaoMap
                  center={mapCenter}
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
        )}{/* uiState === 'ready' */}

      </div>
    </div>
  );
}
