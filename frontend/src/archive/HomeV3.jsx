import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  mockMainData,
  mockRouteData,
  mockMember,
  mockScheduleList,
} from '../data/mockData';
import './HomeV3.css';

/* ================================================================
   유틸
   ================================================================ */

function getGreeting(hour) {
  if (hour >= 5  && hour < 9)  return '좋은 아침이에요';
  if (hour >= 9  && hour < 12) return '오전도 힘내요';
  if (hour >= 12 && hour < 14) return '잠깐 쉬어가세요';
  if (hour >= 14 && hour < 18) return '오후도 함께할게요';
  if (hour >= 18 && hour < 21) return '좋은 저녁이에요';
  return '늦은 시간이네요';
}

function formatDateKo(date) {
  const DAYS = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${DAYS[date.getDay()]}`;
}

function isoToHHMM(isoString) {
  const d = new Date(isoString);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function calcCountdown(reminderAt) {
  const now    = new Date();
  const src    = new Date(reminderAt);
  const target = new Date(
    now.getFullYear(), now.getMonth(), now.getDate(),
    src.getHours(), src.getMinutes(), 0,
  );
  const diff = target - now;
  return diff > 0 ? Math.ceil(diff / 60_000) : 0;
}

function formatCountdown(minutes) {
  if (minutes < 60) return { num: String(minutes), unit: '분' };
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0
    ? { num: `${h}`, unit: '시간' }
    : { num: `${h}시간 ${m}`, unit: '분' };
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
   내비게이션 탭 정의
   ================================================================ */

const NAV_TABS = [
  { label: '홈',     path: '/v3'          },
  { label: '캘린더', path: '/v3/calendar' },
  { label: '설정',   path: '/settings'    },
];

/* ── 데스크톱 상단 바 ── */
function TopBar() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return (
    <header className="hv3-topbar">
      <span className="hv3-topbar__brand">오늘어디</span>
      <nav className="hv3-topbar__nav">
        {NAV_TABS.map(tab => {
          const active = pathname === tab.path;
          return (
            <button
              key={tab.path}
              className={`hv3-topbar__link${active ? ' hv3-topbar__link--on' : ''}`}
              onClick={() => navigate(tab.path)}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>
    </header>
  );
}

/* ── 모바일 하단 탭 ── */
function BottomTab() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return (
    <nav className="hv3-bottomtab">
      {NAV_TABS.map(tab => {
        const active = pathname === tab.path;
        return (
          <button
            key={tab.path}
            className={`hv3-bottomtab__item${active ? ' hv3-bottomtab__item--on' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            <span className="hv3-bottomtab__dot-wrap">
              {active && <span className="hv3-bottomtab__dot" />}
            </span>
            <span className="hv3-bottomtab__label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

/* ================================================================
   빈 상태
   ================================================================ */

function EmptyView() {
  const navigate = useNavigate();
  return (
    <div className="hv3-empty hv3-s hv3-s--2">
      <p className="hv3-empty__main">등록된 일정이 없어요</p>
      <p className="hv3-empty__sub">캘린더에서 첫 일정을 추가해보세요</p>
      <button className="hv3-empty__btn" onClick={() => navigate('/calendar')}>
        캘린더 열기
      </button>
    </div>
  );
}

/* ================================================================
   메인
   ================================================================ */

export default function HomeV3() {
  const navigate  = useNavigate();
  const now       = useMemo(() => new Date(), []);
  const nickname  = mockMember.data.nickname;
  const allScheds = mockScheduleList.data;
  const cand      = mockRouteData.data.candidates[0];

  const [activeIdx, setActiveIdx] = useState(0);

  const schedule = useMemo(() => {
    const s = allScheds[activeIdx];
    if (!s) return null;
    return {
      ...s,
      reminderOffsetMinutes: activeIdx === 0
        ? mockMainData.data.nearestSchedule.reminderOffsetMinutes
        : 30,
      hasPrecalculatedRoute: activeIdx === 0,
    };
  }, [activeIdx, allScheds]);

  const nextSch  = allScheds[activeIdx + 1] ?? null;
  const hasPrev  = activeIdx > 0;
  const hasRoute = schedule?.hasPrecalculatedRoute ?? false;

  const [countdown, setCountdown] = useState(
    schedule ? calcCountdown(schedule.reminderAt) : 0,
  );

  useEffect(() => {
    setCountdown(schedule ? calcCountdown(schedule.reminderAt) : 0);
    if (!schedule) return;
    const id = setInterval(
      () => setCountdown(calcCountdown(schedule.reminderAt)),
      60_000,
    );
    return () => clearInterval(id);
  }, [schedule]);

  const [heroH, heroM] = schedule
    ? schedule.arrivalTime.substring(11, 16).split(':')
    : ['--', '--'];

  const timeline = useMemo(
    () => hasRoute ? buildTimeline(cand.segments) : [],
    [hasRoute, cand],
  );

  const totalWalkingMeters = useMemo(
    () => cand.segments
      .filter(s => s.mode === 'WALK')
      .reduce((sum, s) => sum + (s.distanceMeters ?? 0), 0),
    [cand],
  );

  const busSegs = useMemo(
    () => hasRoute
      ? cand.segments.filter(s => s.mode === 'BUS' || s.mode === 'SUBWAY')
      : [],
    [hasRoute, cand],
  );

  const { num: cdNum, unit: cdUnit } = formatCountdown(countdown);

  return (
    <div className="hv3">

      {/* 데스크톱 상단 바 */}
      <TopBar />

      {/* 메인 바디 */}
      <div className="hv3-body">
        <div className="hv3-layout">

          {/* ── 왼쪽 열: 날짜 / 히어로 / 출발 ── */}
          <div className="hv3-layout__left">

            {/* 섹션 1: 날짜 + 인사 + 이전 일정 네비 */}
            <section className="hv3-s hv3-s--1">
              {hasPrev && (
                <button
                  className="hv3-nav-prev"
                  onClick={() => setActiveIdx(i => i - 1)}
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                    <path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="1.5"
                      strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  이전 일정
                </button>
              )}
              <p className="hv3-date">{formatDateKo(now)}</p>
              <p className="hv3-greeting">{getGreeting(now.getHours())}, {nickname}님</p>
            </section>

            <div className="hv3-rule" />

            {/* 섹션 2: 도착 시간 히어로 */}
            <section className="hv3-s hv3-s--2">
              <p className="hv3-hero-label">다음 일정까지</p>
              <div className="hv3-hero-time">
                <span className="hv3-hero-digit">{heroH}</span>
                <span className="hv3-hero-colon">:</span>
                <span className="hv3-hero-digit">{heroM}</span>
              </div>
              <p className="hv3-hero-title">{schedule?.title ?? '일정 없음'}</p>
            </section>

            <div className="hv3-rule" />

            {/* 섹션 3: 출발 안내 */}
            {schedule && (
              <section className="hv3-s hv3-s--3">
                <div className="hv3-depart">
                  <div className="hv3-depart__block">
                    <p className="hv3-micro-label">출발</p>
                    <p className="hv3-depart__time">{isoToHHMM(schedule.reminderAt)}</p>
                  </div>
                  <div className="hv3-depart__block hv3-depart__block--r">
                    <p className="hv3-micro-label">남은 시간</p>
                    <p className="hv3-depart__countdown">
                      <span className="hv3-depart__num">{cdNum}</span>
                      <span className="hv3-depart__unit">{cdUnit}</span>
                    </p>
                  </div>
                </div>
                <p className="hv3-depart__hint">여유 {schedule.reminderOffsetMinutes}분 포함</p>

                {/* 탑승 버스 추천 */}
                {busSegs.length > 0 && (
                  <div className="hv3-bus-chips">
                    {busSegs.map((seg, i) => (
                      <div key={i} className="hv3-bus-chip">
                        <span className="hv3-bus-chip__badge">
                          {seg.mode === 'SUBWAY' ? '지하철' : '버스'}
                        </span>
                        <span className="hv3-bus-chip__line">{seg.lineName}</span>
                        <span className="hv3-bus-chip__stop">{seg.from} 승차</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}

            {/* 모바일에서 열 전환 구분선 */}
            <div className="hv3-rule hv3-rule--col-sep" />

          </div>

          {/* ── 오른쪽 열: 경로 상세 ── */}
          <div className="hv3-layout__right">

            {!schedule ? <EmptyView /> : <>

              {/* 섹션 4: 경로 타임라인 */}
              <section className="hv3-s hv3-s--4">
                <p className="hv3-section-label">경로</p>
                {hasRoute ? (
                  <div className="hv3-tl">
                    {timeline.map((stop, i) => (
                      <div key={i} className="hv3-tl__row">
                        <div className="hv3-tl__track">
                          <div className={`hv3-tl__dot hv3-tl__dot--${stop.type}`} />
                          {i < timeline.length - 1 && <div className="hv3-tl__line" />}
                        </div>
                        <div className="hv3-tl__text">
                          <p className={`hv3-tl__name${stop.type === 'mid' ? ' hv3-tl__name--stop' : ''}`}>
                            {stop.name}
                          </p>
                          {stop.detail && <p className="hv3-tl__detail">{stop.detail}</p>}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="hv3-no-route">경로 정보를 준비 중이에요</p>
                )}
              </section>

              {hasRoute && <>
                <div className="hv3-rule" />

                {/* 섹션 5: 경로 요약 */}
                <section className="hv3-s hv3-s--5 hv3-summary">
                  <div className="hv3-summary__col">
                    <span className="hv3-summary__num">{cand.totalDurationMinutes}</span>
                    <span className="hv3-summary__unit">분 소요</span>
                  </div>
                  <div className="hv3-summary__vline" />
                  <div className="hv3-summary__col">
                    <span className="hv3-summary__num">{cand.totalTransfers}</span>
                    <span className="hv3-summary__unit">회 환승</span>
                  </div>
                  <div className="hv3-summary__vline" />
                  <div className="hv3-summary__col">
                    <span className="hv3-summary__num">{totalWalkingMeters}</span>
                    <span className="hv3-summary__unit">m 도보</span>
                  </div>
                </section>

                <div className="hv3-rule" />

                {/* 섹션 6: 버튼 */}
                <section className="hv3-s hv3-s--6">
                  <button className="hv3-cta" onClick={() => navigate('/v3/route')}>
                    지도에서 경로 보기
                  </button>
                </section>
              </>}

              {/* 섹션 7: 다음 일정 — 클릭으로 전환 */}
              {nextSch && (
                <>
                  <div className="hv3-rule" />
                  <button
                    className="hv3-s hv3-s--7 hv3-next"
                    onClick={() => setActiveIdx(i => i + 1)}
                  >
                    <p className="hv3-micro-label hv3-micro-label--faint">다음 일정</p>
                    <div className="hv3-next__row">
                      <p className="hv3-next__text">
                        {nextSch.arrivalTime.substring(11, 16)}&ensp;
                        {nextSch.title}&ensp;·&ensp;{nextSch.destination.name}
                      </p>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                        <path d="M9 18l6-6-6-6" stroke="currentColor" strokeWidth="1.5"
                          strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </>
              )}

            </>}

            <div className="hv3-spacer" />
          </div>

        </div>
      </div>

      {/* 모바일 하단 탭 */}
      <BottomTab />

    </div>
  );
}
