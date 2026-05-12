import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { mockScheduleList } from '../data/mockData';
import './CalendarV4.css';

/* ── Helpers ──────────────────────────────────────────────────────── */
function isoToHHMM(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function calcDep(arrIso, durMin) {
  const dep = new Date(new Date(arrIso).getTime() - durMin * 60000);
  return `${String(dep.getHours()).padStart(2,'0')}:${String(dep.getMinutes()).padStart(2,'0')}`;
}

function scheduleToForm(s) {
  return {
    title: s.title,
    originName: s.origin.name,
    destinationName: s.destination.name,
    arrivalTime: isoToHHMM(s.arrivalTime),
    daysOfWeek: s.routineRule?.daysOfWeek ?? [],
  };
}

const EMPTY_FORM = {
  title: '', originName: '', destinationName: '', arrivalTime: '09:00', daysOfWeek: [],
};

const WEEK_DISPLAY = [
  { eng: 'MON', ko: '월', jsDay: 1 },
  { eng: 'TUE', ko: '화', jsDay: 2 },
  { eng: 'WED', ko: '수', jsDay: 3 },
  { eng: 'THU', ko: '목', jsDay: 4 },
  { eng: 'FRI', ko: '금', jsDay: 5 },
  { eng: 'SAT', ko: '토', jsDay: 6 },
  { eng: 'SUN', ko: '일', jsDay: 0 },
];

/* ── Nav ──────────────────────────────────────────────────────────── */
const NAV_TABS = [
  { label: '홈',   path: '/v4'          },
  { label: '루틴', path: '/v4/calendar' },
  { label: '설정', path: '/settings'    },
];

function BottomNav() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return (
    <nav className="cv4-nav">
      {NAV_TABS.map(tab => {
        const active = pathname === tab.path;
        return (
          <button
            key={tab.path}
            className={`cv4-nav__item${active ? ' cv4-nav__item--on' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            <span className="cv4-nav__dot-wrap">
              {active && <span className="cv4-nav__dot" />}
            </span>
            <span className="cv4-nav__label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

/* ── RoutineCard ──────────────────────────────────────────────────── */
function RoutineCard({ schedule, todayDow, onEdit, onDelete, animDelay, removing }) {
  const arrTime = isoToHHMM(schedule.arrivalTime);
  const depTime = calcDep(schedule.arrivalTime, schedule.averageDurationMinutes);
  const active  = schedule.routineRule?.daysOfWeek ?? [];

  /* 활성 요일만 추출 (WEEK_DISPLAY 순서 유지) */
  const activeDays = WEEK_DISPLAY.filter(({ eng }) => active.includes(eng));
  const dayFontSize = activeDays.length <= 1 ? 18 : activeDays.length <= 3 ? 15 : 13;

  return (
    <div
      className={`cv4-card${removing ? ' cv4-card--removing' : ''}`}
      style={{ animationDelay: `${animDelay}s` }}
    >
      {/* ── 왼쪽: 요일 패널 ── */}
      <div className="cv4-card__days-panel">
        {activeDays.map(({ eng, ko, jsDay }) => {
          const isToday = jsDay === todayDow;
          return (
            <div
              key={eng}
              className={`cv4-card__day-item${isToday ? ' cv4-card__day-item--today' : ''}`}
              style={{ fontSize: `${dayFontSize}px` }}
            >
              {ko}
            </div>
          );
        })}
      </div>

      {/* ── 오른쪽: 일정 정보 ── */}
      <div className="cv4-card__info">

        {/* 제목 + 도착 시간 */}
        <div className="cv4-card__head">
          <span className="cv4-card__title">{schedule.title}</span>
          <span className="cv4-card__arr">{arrTime}</span>
        </div>

        {/* 출발 → 도착지 */}
        <div className="cv4-card__route">
          {schedule.origin.name} → {schedule.destination.name}
        </div>

        {/* 출발 안내 */}
        <div className="cv4-card__depart">
          <span className="cv4-card__depart-main">{depTime} 출발 → {arrTime} 도착</span>
          <span className="cv4-card__depart-sub">{schedule.averageDurationMinutes}분 · 환승 1회 · 여유 30분</span>
        </div>

        {/* 하단 액션 */}
        <div className="cv4-card__actions">
          <button className="cv4-card__route-btn">경로 보기</button>
          <button className="cv4-card__act" onClick={() => onEdit(schedule)}>수정</button>
          <button className="cv4-card__act cv4-card__act--del" onClick={() => onDelete(schedule)}>삭제</button>
        </div>

      </div>
    </div>
  );
}

/* ── AddEditSheet ─────────────────────────────────────────────────── */
function AddEditSheet({ mode, init, onSave, onCancel }) {
  const [form, setForm] = useState(init ?? EMPTY_FORM);
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));
  const toggleDay = (e) => setForm(f => ({
    ...f,
    daysOfWeek: f.daysOfWeek.includes(e)
      ? f.daysOfWeek.filter(d => d !== e)
      : [...f.daysOfWeek, e],
  }));

  return (
    <div className="cv4-overlay" onClick={onCancel}>
      <div className="cv4-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv4-sheet__handle" />
        <div className="cv4-sheet__heading">
          {mode === 'add' ? '새 루틴 추가' : '루틴 수정'}
        </div>

        <input
          className="cv4-sheet__input"
          placeholder="루틴 이름"
          value={form.title}
          onChange={e => set('title', e.target.value)}
        />

        <div className="cv4-sheet__hint-group">
          <input
            className="cv4-sheet__input"
            placeholder="출발지 검색"
            value={form.originName}
            onChange={e => set('originName', e.target.value)}
          />
          <span className="cv4-sheet__hint">기본: 우이동</span>
        </div>

        <input
          className="cv4-sheet__input"
          placeholder="도착지 검색"
          value={form.destinationName}
          onChange={e => set('destinationName', e.target.value)}
        />

        <div className="cv4-sheet__hint-group">
          <input
            className="cv4-sheet__input cv4-sheet__input--time"
            type="time"
            value={form.arrivalTime}
            onChange={e => set('arrivalTime', e.target.value)}
          />
          <span className="cv4-sheet__hint">도착 희망 시간</span>
        </div>

        <div className="cv4-sheet__days-block">
          <span className="cv4-sheet__days-label">반복 요일</span>
          <div className="cv4-sheet__days-row">
            {WEEK_DISPLAY.map(({ eng, ko }) => (
              <button
                key={eng}
                className={`cv4-sheet__daybtn${form.daysOfWeek.includes(eng) ? ' cv4-sheet__daybtn--on' : ''}`}
                onClick={() => toggleDay(eng)}
              >
                {ko}
              </button>
            ))}
          </div>
        </div>

        <div className="cv4-sheet__footer">
          <button className="cv4-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv4-sheet__save" onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── DeleteDialog ─────────────────────────────────────────────────── */
function DeleteDialog({ schedule, onConfirm, onCancel }) {
  return (
    <div className="cv4-overlay cv4-overlay--center" onClick={onCancel}>
      <div className="cv4-dialog" onClick={e => e.stopPropagation()}>
        <p className="cv4-dialog__msg">이 루틴을 삭제할까요?</p>
        <p className="cv4-dialog__name">{schedule.title}</p>
        <div className="cv4-dialog__btns">
          <button className="cv4-dialog__cancel" onClick={onCancel}>취소</button>
          <button className="cv4-dialog__del" onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   CalendarV4
   ================================================================ */
export default function CalendarV4() {
  const todayDow = new Date().getDay();

  const [schedules,    setSchedules]    = useState(mockScheduleList.data);
  const [sheetState,   setSheetState]   = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [removingId,   setRemovingId]   = useState(null);

  /* 루틴만 (반복 일정) */
  const routines = schedules.filter(s => s.routineRule?.daysOfWeek?.length > 0);

  /* 추가 */
  const handleAddSave = (form) => {
    const [h, m] = form.arrivalTime.split(':');
    const base = new Date();
    base.setHours(parseInt(h,10), parseInt(m,10), 0, 0);
    setSchedules(prev => [...prev, {
      scheduleId: `sch_${Date.now()}`,
      title: form.title || '새 루틴',
      origin:      { name: form.originName      || '출발지' },
      destination: { name: form.destinationName || '도착지' },
      arrivalTime: base.toISOString(),
      reminderAt:  base.toISOString(),
      averageDurationMinutes: 30,
      routineRule: {
        type: 'WEEKLY',
        daysOfWeek: form.daysOfWeek.length > 0 ? form.daysOfWeek : ['MON'],
      },
    }]);
    setSheetState(null);
  };

  /* 수정 */
  const handleEditSave = (form) => {
    setSchedules(prev => prev.map(s => {
      if (s.scheduleId !== sheetState.scheduleId) return s;
      const [h, m] = form.arrivalTime.split(':');
      const arr = new Date(s.arrivalTime);
      arr.setHours(parseInt(h,10), parseInt(m,10), 0, 0);
      return {
        ...s,
        title:       form.title       || s.title,
        origin:      { name: form.originName      || s.origin.name },
        destination: { name: form.destinationName || s.destination.name },
        arrivalTime: arr.toISOString(),
        routineRule: {
          type: 'WEEKLY',
          daysOfWeek: form.daysOfWeek.length > 0
            ? form.daysOfWeek : (s.routineRule?.daysOfWeek ?? ['MON']),
        },
      };
    }));
    setSheetState(null);
  };

  /* 삭제 (fade-out 후 제거) */
  const handleDeleteConfirm = () => {
    const id = deleteTarget.scheduleId;
    setRemovingId(id);
    setTimeout(() => {
      setSchedules(prev => prev.filter(s => s.scheduleId !== id));
      setRemovingId(null);
      setDeleteTarget(null);
    }, 300);
  };

  return (
    <div className="cv4">

      {/* ── 헤더 ── */}
      <header className="cv4-header">
        <span className="cv4-header__title">루틴</span>
        <span className="cv4-header__badge">{routines.length}개 활성</span>
      </header>

      {/* ── 카드 목록 ── */}
      <div className="cv4-body">
        <div className="cv4-cards">

          {routines.length === 0 ? (
            <div className="cv4-empty">
              <p className="cv4-empty__main">등록된 루틴이 없어요</p>
              <p className="cv4-empty__sub">반복 일정을 추가해보세요</p>
            </div>
          ) : (
            routines.map((s, i) => (
              <RoutineCard
                key={s.scheduleId}
                schedule={s}
                todayDow={todayDow}
                animDelay={i * 0.15}
                removing={removingId === s.scheduleId}
                onEdit={sc => setSheetState({
                  mode: 'edit',
                  scheduleId: sc.scheduleId,
                  init: scheduleToForm(sc),
                })}
                onDelete={sc => setDeleteTarget(sc)}
              />
            ))
          )}

          {/* 새 루틴 추가 카드 */}
          <button className="cv4-add-card" onClick={() => setSheetState({ mode: 'add' })}>
            + 새 루틴 추가
          </button>

        </div>
      </div>

      <BottomNav />

      {/* ── 바텀시트 ── */}
      {sheetState && (
        <AddEditSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={sheetState.mode === 'add' ? handleAddSave : handleEditSave}
          onCancel={() => setSheetState(null)}
        />
      )}

      {/* ── 삭제 확인 ── */}
      {deleteTarget && (
        <DeleteDialog
          schedule={deleteTarget}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
