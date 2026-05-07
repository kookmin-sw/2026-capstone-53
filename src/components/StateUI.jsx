import React from 'react';
import './StateUI.css';

function Sk({ className }) {
  return <div className={`skeleton ${className}`} />;
}

/* ── Home skeleton ── */
export function HomeSkeletons() {
  return (
    <div className="skel-home">
      <Sk className="skel-home__card" />
      <div className="skel-home__bento">
        <Sk className="skel-home__bento-a" />
        <Sk className="skel-home__bento-b" />
        <Sk className="skel-home__bento-c" />
        <Sk className="skel-home__bento-d" />
      </div>
      <Sk className="skel-home__map" />
    </div>
  );
}

/* ── Calendar skeleton ── */
export function CalendarSkeletons() {
  return (
    <div className="skel-cal">
      <Sk className="skel-cal__calendar" />
      <Sk className="skel-cal__card" />
      <Sk className="skel-cal__card" />
      <Sk className="skel-cal__card" />
    </div>
  );
}

/* ── Settings skeleton ── */
export function SettingsSkeletons() {
  return (
    <div className="skel-st">
      <Sk className="skel-st__profile" />
      <Sk className="skel-st__section" />
      <Sk className="skel-st__section--sm" />
    </div>
  );
}

/* ── Empty state (홈 — 일정 없음) ── */
export function HomeEmpty({ onCalendar }) {
  return (
    <div className="state-box">
      <p className="state-box__title">등록된 일정이 없어요</p>
      <p className="state-box__desc">캘린더에서 첫 일정을 추가해보세요</p>
      <button className="state-box__btn" onClick={onCalendar}>
        캘린더 열기
      </button>
    </div>
  );
}

/* ── Error state ── */
export function ErrorState({ onRetry }) {
  return (
    <div className="state-box">
      <p className="state-box__title">데이터를 불러올 수 없습니다</p>
      <p className="state-box__desc">네트워크 연결을 확인해주세요</p>
      <button className="state-box__btn state-box__btn--outline" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}
