import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  mockMainData,
  mockRouteData,
  mockMember,
  mockScheduleList,
} from '../data/mockData';
import './HomeV4.css';

/* ================================================================
   상수 / 유틸
   ================================================================ */

const CIRC = 2 * Math.PI * 26;

function formatDateShort(d) {
  const DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')} ${DAYS[d.getDay()]}`;
}

function isoToHHMM(iso) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function calcCountdown(reminderAt) {
  const now = new Date();
  const src = new Date(reminderAt);
  const t   = new Date(now.getFullYear(), now.getMonth(), now.getDate(),
                       src.getHours(), src.getMinutes(), 0);
  const diff = t - now;
  return diff > 0 ? Math.ceil(diff / 60_000) : 0;
}

function formatCountdownShort(min) {
  if (min <= 0) return '지금 출발';
  if (min < 60) return `${min}분 후`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m === 0 ? `${h}시간 후` : `${h}시간 ${m}분 후`;
}

const MODE_ICON = { WALK: '🚶', BUS: '🚌', SUBWAY: '🚇' };

/* ================================================================
   카운트다운 원형 SVG
   ================================================================ */

function CountdownCircle({ minutes }) {
  const [offset, setOffset] = useState(CIRC);

  useEffect(() => {
    const id = setTimeout(() => {
      setOffset(CIRC * (1 - Math.min(minutes / 60, 1)));
    }, 300);
    return () => clearTimeout(id);
  }, [minutes]);

  const num  = minutes < 60 ? String(minutes) : `${Math.floor(minutes / 60)}h`;
  const unit = minutes < 60 ? 'min' : 'hr';

  return (
    <div className="hv4-circle">
      <svg viewBox="0 0 64 64" className="hv4-circle__svg">
        <circle cx="32" cy="32" r="26" className="hv4-circle__track" />
        <circle cx="32" cy="32" r="26" className="hv4-circle__ring"
          style={{ strokeDashoffset: offset }} />
      </svg>
      <div className="hv4-circle__inner">
        <span className="hv4-circle__num">{num}</span>
        <span className="hv4-circle__unit">{unit}</span>
      </div>
    </div>
  );
}

/* ================================================================
   가로 경로 프로그레스 바
   ================================================================ */

function RouteProgress({ segments }) {
  const total = segments.reduce((s, seg) => s + seg.durationMinutes, 0);

  return (
    <div className="hv4-progress">
      <div className="hv4-progress__bar">
        {segments.map((seg, i) => (
          <div
            key={i}
            className={`hv4-progress__seg hv4-progress__seg--${seg.mode.toLowerCase()}`}
            style={{
              width: `${(seg.durationMinutes / total) * 100}%`,
              animationDelay: `${0.6 + i * 0.25}s`,
            }}
          />
        ))}
      </div>

      <div className="hv4-progress__labels">
        {segments.map((seg, i) => (
          <React.Fragment key={i}>
            <div className="hv4-seg">
              <div className="hv4-seg__top">
                <span className="hv4-seg__icon">{MODE_ICON[seg.mode] ?? '·'}</span>
                <span className="hv4-seg__min">{seg.durationMinutes}분</span>
              </div>
              {seg.lineName && <p className="hv4-seg__line">{seg.lineName}번</p>}
              {seg.mode === 'WALK' && seg.distanceMeters && (
                <p className="hv4-seg__line">{seg.distanceMeters}m</p>
              )}
            </div>
            {i < segments.length - 1 && (
              <span className="hv4-seg__arrow">→</span>
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

/* ================================================================
   시간 / 날짜 배지
   ================================================================ */

function TimeBadge({ isoTime }) {
  const d = new Date(isoTime);
  const h = String(d.getHours()).padStart(2, '0');
  const m = `:${String(d.getMinutes()).padStart(2, '0')}`;
  return (
    <div className="hv4-badge">
      <span className="hv4-badge__h">{h}</span>
      <span className="hv4-badge__m">{m}</span>
    </div>
  );
}

function DateBadge({ num }) {
  return (
    <div className="hv4-badge">
      <span className="hv4-badge__h">{num}</span>
    </div>
  );
}

/* ================================================================
   하단 네비게이션
   ================================================================ */

const NAV_TABS = [
  { label: '홈',     path: '/v4'       },
  { label: '루틴',   path: '/v4/calendar' },
  { label: '설정',   path: '/settings' },
];

function BottomNav() {
  const navigate    = useNavigate();
  const { pathname } = useLocation();
  return (
    <nav className="hv4-nav">
      {NAV_TABS.map(tab => {
        const active = pathname === tab.path;
        return (
          <button
            key={tab.path}
            className={`hv4-nav__item${active ? ' hv4-nav__item--on' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            <span className="hv4-nav__dot-wrap">
              {active && <span className="hv4-nav__dot" />}
            </span>
            <span className="hv4-nav__label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

/* ================================================================
   카드 1: 메인 일정
   ================================================================ */

function MainCard({ schedule, cand, animDelay }) {
  const navigate = useNavigate();
  const segments = cand.segments;

  const totalWalkingMeters = useMemo(
    () => segments.filter(s => s.mode === 'WALK').reduce((sum, s) => sum + (s.distanceMeters ?? 0), 0),
    [segments],
  );

  // 도착 시간 카운트업 (00:00 → 09:00, 1.2초)
  const targetMin = useMemo(() => {
    const d = new Date(schedule.arrivalTime);
    return d.getHours() * 60 + d.getMinutes();
  }, [schedule.arrivalTime]);

  const [displayTime, setDisplayTime] = useState('00:00');
  const rafRef = useRef(null);

  useEffect(() => {
    let start = null;
    const DURATION = 1200;
    function tick(ts) {
      if (!start) start = ts;
      const p = Math.min((ts - start) / DURATION, 1);
      const e = 1 - Math.pow(1 - p, 3);
      const cur = Math.floor(e * targetMin);
      setDisplayTime(
        `${String(Math.floor(cur / 60)).padStart(2, '0')}:${String(cur % 60).padStart(2, '0')}`
      );
      if (p < 1) rafRef.current = requestAnimationFrame(tick);
    }
    rafRef.current = requestAnimationFrame(tick);
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
  }, [targetMin]);

  // 카운트다운
  const [countdown, setCountdown] = useState(() => calcCountdown(schedule.reminderAt));
  const [cdFade, setCdFade]        = useState(false);

  useEffect(() => {
    const id = setInterval(() => {
      setCdFade(true);
      setTimeout(() => {
        setCountdown(calcCountdown(schedule.reminderAt));
        setCdFade(false);
      }, 300);
    }, 60_000);
    return () => clearInterval(id);
  }, [schedule.reminderAt]);

  const departTime = isoToHHMM(schedule.reminderAt);

  return (
    <div className="hv4-card hv4-card--main" style={{ animationDelay: `${animDelay}s` }}>
      {/* 도착 시간 (대형) */}
      <div className={`hv4-big-time${cdFade ? ' hv4-big-time--fade' : ''}`}>
        {displayTime}
      </div>
      <p className="hv4-card__title">{schedule.title}</p>
      <p className="hv4-card__route">
        {schedule.origin.name} → {schedule.destination.name}
      </p>

      {/* 출발 블록 */}
      <div className="hv4-depart-block">
        <div className="hv4-depart-block__left">
          <span className="hv4-depart-block__label">출발</span>
          <span className="hv4-depart-block__time">{departTime}</span>
        </div>
        <CountdownCircle minutes={countdown} />
      </div>
      <p className="hv4-depart-block__hint">여유 {schedule.reminderOffsetMinutes}분 포함</p>

      {/* 가로 경로 */}
      <RouteProgress segments={segments} />

      {/* 요약 3칸 */}
      <div className="hv4-stats">
        <div className="hv4-stat">
          <span className="hv4-stat__num">{cand.totalDurationMinutes}</span>
          <span className="hv4-stat__label">분 소요</span>
        </div>
        <div className="hv4-stat">
          <span className="hv4-stat__num">{cand.totalTransfers}</span>
          <span className="hv4-stat__label">환승</span>
        </div>
        <div className="hv4-stat">
          <span className="hv4-stat__num">{totalWalkingMeters}</span>
          <span className="hv4-stat__label">m 도보</span>
        </div>
      </div>

      <button className="hv4-btn-primary" onClick={() => navigate('/v3/route')}>
        경로 보기 →
      </button>
    </div>
  );
}

/* ================================================================
   카드 2: 다음 일정 (아코디언)
   ================================================================ */

function NextCard({ schedule, animDelay }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="hv4-card hv4-card--collapsible" style={{ animationDelay: `${animDelay}s` }}>
      <button className="hv4-trigger" onClick={() => setOpen(o => !o)}>
        <div className="hv4-trigger__left">
          <TimeBadge isoTime={schedule.arrivalTime} />
          <div className="hv4-trigger__info">
            <p className="hv4-card__title">{schedule.title}</p>
            <p className="hv4-card__sub">
              {schedule.destination.name} · {schedule.averageDurationMinutes}분
            </p>
          </div>
        </div>
        <span className={`hv4-chevron${open ? ' hv4-chevron--open' : ''}`} aria-hidden="true" />
      </button>

      <div className={`hv4-expand${open ? ' hv4-expand--open' : ''}`}>
        <div className="hv4-expand__depart">
          <span className="hv4-expand__time">{isoToHHMM(schedule.reminderAt)}</span>
          <span className="hv4-expand__word"> 출발</span>
        </div>
        <p className="hv4-expand__sub">
          {schedule.averageDurationMinutes}분 · 환승 1회
        </p>
        <button className="hv4-btn-text">경로 보기</button>
      </div>
    </div>
  );
}

/* ================================================================
   카드 3: 내일 일정 미리보기 (아코디언)
   ================================================================ */

function TomorrowCard({ schedules, animDelay }) {
  const [open, setOpen] = useState(false);

  const tomorrow = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return { num: String(d.getDate()), label: `${d.getMonth() + 1}월 ${d.getDate()}일` };
  }, []);

  return (
    <div className="hv4-card hv4-card--collapsible" style={{ animationDelay: `${animDelay}s` }}>
      <button className="hv4-trigger" onClick={() => setOpen(o => !o)}>
        <div className="hv4-trigger__left">
          <DateBadge num={tomorrow.num} />
          <div className="hv4-trigger__info">
            <p className="hv4-card__title">내일 일정 · {schedules.length}개</p>
            <p className="hv4-card__sub">{tomorrow.label}</p>
          </div>
        </div>
        <span className={`hv4-chevron${open ? ' hv4-chevron--open' : ''}`} aria-hidden="true" />
      </button>

      <div className={`hv4-expand${open ? ' hv4-expand--open' : ''}`}>
        <div className="hv4-tomorrow-list">
          {schedules.map((s, i) => (
            <div key={s.scheduleId} className={`hv4-tomorrow-item${i > 0 ? ' hv4-tomorrow-item--dim' : ''}`}>
              <span className="hv4-tomorrow-item__time">{isoToHHMM(s.arrivalTime)}</span>
              <span className="hv4-tomorrow-item__title">{s.title}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   카드 4: 새 일정 추가
   ================================================================ */

function AddCard({ animDelay }) {
  const navigate = useNavigate();
  return (
    <button
      className="hv4-card hv4-card--add"
      style={{ animationDelay: `${animDelay}s` }}
      onClick={() => navigate('/calendar')}
    >
      <span className="hv4-card__add-text">+ 새 일정 추가</span>
    </button>
  );
}

/* ================================================================
   빈 상태
   ================================================================ */

function EmptyCard() {
  const navigate = useNavigate();
  return (
    <div className="hv4-card hv4-card--empty" style={{ animationDelay: '0s' }}>
      <p className="hv4-empty__main">등록된 일정이 없어요</p>
      <p className="hv4-empty__sub">캘린더에서 첫 일정을 추가해보세요</p>
      <button className="hv4-btn-text hv4-empty__cta" onClick={() => navigate('/calendar')}>
        캘린더 열기
      </button>
    </div>
  );
}

/* ================================================================
   메인 컴포넌트
   ================================================================ */

export default function HomeV4() {
  const now      = useMemo(() => new Date(), []);
  const nickname = mockMember.data.nickname;
  const schedule = mockMainData.data.nearestSchedule;
  const cand     = mockRouteData.data.candidates[0];
  const allScheds = mockScheduleList.data;

  const nextSch        = allScheds[1] ?? null;
  const tomorrowScheds = [allScheds[2], allScheds[3]].filter(Boolean);

  // 상태 바용 카운트다운
  const [statusCountdown, setStatusCountdown] = useState(
    () => schedule ? calcCountdown(schedule.reminderAt) : 0,
  );
  useEffect(() => {
    if (!schedule) return;
    const id = setInterval(() => setStatusCountdown(calcCountdown(schedule.reminderAt)), 60_000);
    return () => clearInterval(id);
  }, [schedule?.reminderAt]);

  const visibleCount  = (schedule ? 1 : 0) + (nextSch ? 1 : 0);
  const statusText    = schedule
    ? `오늘 일정 ${visibleCount}개 · 다음 출발 ${formatCountdownShort(statusCountdown)}`
    : '등록된 일정이 없어요';

  const weather = { temperature: 28 };

  return (
    <div className="hv4">

      {/* 헤더 */}
      <header className="hv4-header">
        <div className="hv4-header__left">
          <span className="hv4-header__name">{nickname}</span>
          <span className="hv4-header__online" />
        </div>
        <div className="hv4-header__right">
          <span className="hv4-header__date-short">{formatDateShort(now)}</span>
          <span className="hv4-header__temp-sm">{weather.temperature}°</span>
        </div>
      </header>

      {/* 상태 요약 */}
      <p className="hv4-status-bar">{statusText}</p>

      {/* 카드 목록 */}
      <div className="hv4-cards">
        {schedule ? (
          <>
            <MainCard
              schedule={{ ...schedule, reminderOffsetMinutes: schedule.reminderOffsetMinutes ?? 30 }}
              cand={cand}
              animDelay={0}
            />
            {nextSch && <NextCard schedule={nextSch} animDelay={0.15} />}
            {tomorrowScheds.length > 0 && (
              <TomorrowCard schedules={tomorrowScheds} animDelay={0.3} />
            )}
            <AddCard animDelay={0.45} />
          </>
        ) : (
          <>
            <EmptyCard />
            <AddCard animDelay={0.15} />
          </>
        )}
      </div>

      <BottomNav />
    </div>
  );
}
