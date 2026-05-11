import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { useSettings } from '../contexts/SettingsContext';
import { usePushNotification } from '../hooks/usePushNotification';
import { api } from '../api';
import ScheduleCard from '../components/ScheduleCard';
import KakaoMap from '../components/KakaoMap';
import { HomeSkeletons, HomeEmpty, ErrorState } from '../components/StateUI';
import './HomeV2Route.css';
import '../components/RouteCard.css';

function calcReminderTime(departureTime, offsetMinutes) {
  if (!departureTime) return '--:--';
  const time = departureTime.includes('T') ? departureTime.split('T')[1].substring(0, 5) : departureTime;
  const [h, m] = time.split(':').map(Number);
  const total = h * 60 + m - offsetMinutes;
  const rh = Math.floor(((total % 1440) + 1440) % 1440 / 60);
  const rm = ((total % 1440) + 1440) % 1440 % 60;
  return `${String(rh).padStart(2, '0')}:${String(rm).padStart(2, '0')}`;
}

function extractTime(iso) {
  if (!iso) return '--:--';
  return iso.includes('T') ? iso.split('T')[1].substring(0, 5) : iso;
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

function calcMinutesUntil(isoTime) {
  if (!isoTime) return null;
  const now = new Date();
  const target = new Date(isoTime);
  const diff = Math.round((target - now) / 60000);
  return diff > 0 ? diff : null;
}

const MY_LOCATION = { lat: 37.661, lng: 127.012 };

export default function HomeNoMap() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme } = useTheme();
  const pointColor = '#2563EB';
  const { settings } = useSettings();
  const { requestPermission } = usePushNotification();

  const [uiState, setUiState] = useState('loading');
  const [nickname, setNickname] = useState('');
  const [schedules, setSchedules] = useState([]);   // 오늘 일정 목록
  const [routeMap, setRouteMap]   = useState({});    // scheduleId → route data
  const [activeIdx, setActiveIdx] = useState(0);
  const [hoverSide, setHoverSide] = useState(null);
  const [busExpanded, setBusExpanded] = useState(false);
  const [tlOpen, setTlOpen] = useState(false);
  const touchStartX = useRef(null);

  const fetchData = useCallback(async () => {
    setUiState('loading');
    try {
      const [memberData, schData] = await Promise.all([
        api.members.me().catch(() => null),
        api.schedules.list(),
      ]);

      setNickname(memberData?.nickname ?? '');
      const items = schData.items ?? [];

      if (items.length === 0) {
        setSchedules([]);
        setUiState('empty');
        return;
      }

      setSchedules(items);

      // 경로가 계산된 일정들의 경로 병렬 로드
      const calculated = items.filter(s => s.routeStatus === 'CALCULATED');
      const routeResults = await Promise.allSettled(
        calculated.map(s => api.route.get(s.scheduleId))
      );

      const rMap = {};
      routeResults.forEach((result, i) => {
        if (result.status === 'fulfilled') {
          rMap[calculated[i].scheduleId] = result.value;
        }
      });
      setRouteMap(rMap);
      setUiState('ready');
      requestPermission();
    } catch (err) {
      console.error('[Home] 데이터 로드 실패', err);
      setUiState('error');
    }
  }, [requestPermission]);

  useEffect(() => {
    const forced = searchParams.get('state');
    if (forced === 'loading') return;
    if (forced === 'error')  { setUiState('error'); return; }
    if (forced === 'empty')  { setUiState('empty'); return; }
    fetchData();
  }, [searchParams, fetchData]);

  const retry = () => fetchData();

  // 캐러셀 데이터 구성
  const total = schedules.length;
  const currentSch = schedules[activeIdx] ?? null;
  const currentRoute = currentSch ? routeMap[currentSch.scheduleId] : null;
  const segments = currentRoute?.route?.segments ?? [];
  const route = currentRoute?.route ?? null;

  // 벤토용 데이터 추출
  const walkSegs = segments.filter(s => s.mode === 'WALK');
  const transitSeg = segments.find(s => s.mode !== 'WALK');
  const walkMins = walkSegs.reduce((sum, s) => sum + s.durationMinutes, 0) || 0;
  const boardingStop = transitSeg ? { stopName: transitSeg.from || transitSeg.stationStart || '—' } : null;

  const mapCenter = segments.length > 0
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

  const goNext = () => { setActiveIdx(i => Math.min(i + 1, total - 1)); setBusExpanded(false); };
  const goPrev = () => { setActiveIdx(i => Math.max(i - 1, 0)); setBusExpanded(false); };

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

  // 타임라인: 시간 기준 정렬 + 상태 판별
  const now = new Date();
  const nowMins = now.getHours() * 60 + now.getMinutes();
  const toMins = (iso) => {
    const t = extractTime(iso);
    const [h, m] = t.split(':').map(Number);
    return h * 60 + m;
  };
  const sortedSchedules = [...schedules].sort((a, b) =>
    toMins(a.recommendedDepartureTime || a.arrivalTime) - toMins(b.recommendedDepartureTime || b.arrivalTime)
  );
  const nextSchIdx = sortedSchedules.findIndex(s =>
    toMins(s.recommendedDepartureTime || s.arrivalTime) > nowMins
  );

  return (
    <div className="home home--no-map">
      <div className="home__container hnm__container">

        {/* 타이틀 */}
        <div className="home__page-header">
          <div>
            <p className="home__greeting">
              {getGreeting()}{nickname ? `, ${nickname}님` : ''}
            </p>
            <h1 className="home__title">오늘의 경로 안내</h1>
          </div>
        </div>

        {/* 상태별 렌더링 */}
        {uiState === 'loading' && <HomeSkeletons />}
        {uiState === 'error'   && <ErrorState onRetry={retry} />}
        {uiState === 'empty'   && <HomeEmpty onCalendar={() => navigate('/calendar')} />}

        {uiState === 'ready' && currentSch && (
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
                onClick={() => setTlOpen(v => !v)}
              >
                <div
                  className="home__sch-track"
                  style={{ transform: `translateX(calc(-${activeIdx} * 100%))` }}
                >
                  {schedules.map((sch, i) => (
                    <div key={sch.scheduleId} className="home__sch-slide">
                      <ScheduleCard
                        schedule={{
                          title: sch.title,
                          arrivalTime: extractTime(sch.arrivalTime),
                          originName: sch.origin?.name ?? '',
                          destinationName: sch.destination?.name ?? '',
                        }}
                        departureTime={extractTime(sch.recommendedDepartureTime || sch.userDepartureTime)}
                        departureMinutes={calcMinutesUntil(sch.recommendedDepartureTime || sch.userDepartureTime) ?? '—'}
                        bufferMinutes={sch.reminderOffsetMinutes}
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
                    {schedules.map((sch, i) => (
                      <button
                        key={sch.scheduleId}
                        className={`home__dot${i === activeIdx ? ' home__dot--on' : ''}`}
                        onClick={() => setActiveIdx(i)}
                        aria-label={`일정 ${i + 1}`}
                      />
                    ))}
                  </div>
                )}

                {/* 펼침/접힘 표시 */}
                <div className="home__tl-toggle">
                  <svg
                    className={`home__tl-chevron${tlOpen ? ' home__tl-chevron--open' : ''}`}
                    width="16" height="16" viewBox="0 0 24 24" fill="none"
                  >
                    <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2"
                      strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
              </div>

              {/* 오늘의 일정 타임라인 */}
              <div className={`home__today-tl${tlOpen ? ' home__today-tl--open' : ''}`}>
                <div className="home__today-tl__inner">
                  <div className="home__today-tl__header">
                    <span className="home__today-tl__label">오늘의 일정</span>
                    <span className="home__today-tl__count">{schedules.length}개</span>
                  </div>
                  {sortedSchedules.map((sch, i) => {
                    const arrMins = toMins(sch.arrivalTime);
                    const isPast = arrMins < nowMins;
                    const isNext = i === nextSchIdx;
                    const isLast = i === sortedSchedules.length - 1;
                    const deptTime = extractTime(sch.recommendedDepartureTime || sch.userDepartureTime);
                    const arrTime  = extractTime(sch.arrivalTime);
                    return (
                      <div key={sch.scheduleId} className={`htl-item${isPast ? ' htl-item--past' : ''}${isNext ? ' htl-item--next' : ''}`}>
                        <div className="htl-item__line">
                          <div className={`htl-item__dot${isNext ? ' htl-item__dot--point' : ''}`} />
                          {!isLast && <div className="htl-item__connector" />}
                        </div>
                        <div className="htl-item__body">
                          <div className="htl-item__title">{sch.title}</div>
                          <div className="htl-item__time">
                            <span className="htl-item__depart">{deptTime}</span>
                            <span className="htl-item__arrow"> → </span>
                            <span className="htl-item__arrive">{arrTime}</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* 경로 안내 벤토 */}
              {route ? (
                <section className="home__section">
                  <div className="home__section-header">
                    <h2 className="home__section-title">경로 안내</h2>
                    <span className="home__schedule-badge">{currentSch.title}</span>
                  </div>

                  <div className="rc-bento hnm-bento">

                    {/* A: 승차 정류장 */}
                    <div className="rc-cell rc-cell--stop">
                      <span className="rc-label">승차 정류장</span>
                      <span className="rc-stop-name">{boardingStop?.stopName ?? '—'}</span>
                      <span className="rc-walk-badge">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
                          <circle cx="12" cy="5" r="2" fill="currentColor"/>
                          <path d="M9 9l-2 5h3l1 5M15 9l2 5h-3l-1 5M9 9h6"
                            stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        도보 {walkMins}분
                      </span>
                    </div>

                    {/* B: 총 소요시간 */}
                    <div className="rc-cell rc-cell--total">
                      <span className="rc-label">총 소요</span>
                      <span className="rc-big-num">{route.totalDurationMinutes}</span>
                      <span className="rc-unit">분</span>
                    </div>

                    {/* C: 권장 출발 시간 */}
                    <div className="rc-cell rc-cell--depart">
                      <span className="rc-label">권장 출발</span>
                      <span className="rc-depart-time">{extractTime(currentSch.recommendedDepartureTime || currentSch.userDepartureTime)}</span>
                      <div className="rc-depart-row">
                        <svg width="18" height="10" viewBox="0 0 18 10" fill="none">
                          <path d="M1 5H15M11 1l4 4-4 4" stroke={pointColor} strokeWidth="1.8"
                            strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        <span className="rc-arrive-time">{extractTime(currentSch.arrivalTime)} 도착</span>
                      </div>
                      {currentSch.reminderOffsetMinutes != null && (
                        <span className="rc-buffer">여유 {currentSch.reminderOffsetMinutes}분 포함</span>
                      )}

                      {settings.alertEnabled && currentSch.recommendedDepartureTime && (
                        <span className="rc-reminder">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"
                              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                          {calcReminderTime(currentSch.recommendedDepartureTime, settings.alertBufferMinutes)} 알림 예정
                        </span>
                      )}
                    </div>

                    {/* D: 대중교통 + 환승 */}
                    <div
                      className={`rc-cell rc-cell--bus hnm-bus-cell${busExpanded ? ' hnm-bus-cell--on' : ''}`}
                      onClick={() => setBusExpanded(v => !v)}
                      role="button"
                      aria-expanded={busExpanded}
                    >
                      <div className="hnm-bus-cell__top">
                        <span className="rc-label">{transitSeg?.mode === 'SUBWAY' ? '지하철' : '버스'}</span>
                        <svg
                          className={`hnm-bus-chevron${busExpanded ? ' hnm-bus-chevron--up' : ''}`}
                          width="14" height="14" viewBox="0 0 24 24" fill="none"
                        >
                          <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2.2"
                            strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      </div>
                      <span className="rc-bus-num">{transitSeg?.lineName || '—'}</span>
                      <span className="rc-arrival">
                        <span className="rc-pulse" />
                        {transitSeg?.durationMinutes ?? '—'}분 소요
                      </span>
                      <div className="rc-transfer-chip">
                        환승 {Math.max(0, (route.transferCount ?? 0) - 1) === 0 ? '없음' : `${Math.max(0, (route.transferCount ?? 0) - 1)}회`}
                      </div>
                    </div>

                    {/* E: 구간별 소요 타임라인 */}
                    {busExpanded && (
                      <div className="rc-cell rc-cell--timeline">
                        <span className="rc-label">구간별 소요</span>
                        <div className="rc-tl-row">
                          {segments.map((seg, i) => (
                            <React.Fragment key={i}>
                              <div className="rc-tl-item">
                                <div className={`rc-tl-icon rc-tl-icon--${seg.mode === 'WALK' ? 'walk' : 'bus'}`}>
                                  {seg.mode === 'WALK' ? (
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                                      <circle cx="12" cy="5" r="2" fill="currentColor"/>
                                      <path d="M9 9l-2 5h3l1 5M15 9l2 5h-3l-1 5M9 9h6"
                                        stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                                    </svg>
                                  ) : (
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                                      <rect x="2" y="5" width="20" height="14" rx="3" fill="currentColor" fillOpacity="0.15"/>
                                      <rect x="2" y="5" width="20" height="14" rx="3" stroke="currentColor" strokeWidth="1.8"/>
                                      <path d="M2 10H22M7 19V21M17 19V21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
                                    </svg>
                                  )}
                                </div>
                                <span className="rc-tl-val">{seg.durationMinutes}분</span>
                                <span className="rc-tl-sub">{seg.mode === 'WALK' ? '도보' : seg.lineName || '대중교통'}</span>
                              </div>
                              {i < segments.length - 1 && (
                                <svg className="rc-tl-arrow" width="16" height="10" viewBox="0 0 16 10" fill="none">
                                  <path d="M1 5H13M9 1l4 4-4 4" stroke="#C5BFB8" strokeWidth="1.5"
                                    strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                              )}
                            </React.Fragment>
                          ))}
                          <span className="rc-tl-eq">=</span>
                          <div className="rc-tl-item rc-tl-item--total">
                            <span className="rc-tl-val rc-tl-val--blue">총 {route.totalDurationMinutes}분</span>
                            <span className="rc-tl-sub">소요</span>
                          </div>
                        </div>
                      </div>
                    )}

                  </div>
                </section>
              ) : (
                <section className="home__section">
                  <div className="home__section-header">
                    <h2 className="home__section-title">경로 안내</h2>
                    <span className="home__schedule-badge">{currentSch.title}</span>
                  </div>
                  <div className="rc-bento hnm-bento">
                    <div className="rc-cell" style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '24px 0', color: '#A09A93', fontSize: 13 }}>
                      경로를 계산하고 있어요...
                    </div>
                  </div>
                </section>
              )}

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
                  stops={boardingStop ? [boardingStop] : []}
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
