import React, { useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { mockScheduleList } from '../data/mockData';
import './CalendarV3.css';

/* ── Helpers ──────────────────────────────────────────────────────── */
const DOW_ENG  = ['SUN','MON','TUE','WED','THU','FRI','SAT'];
const DOW_KO   = ['일','월','화','수','목','금','토'];
const WD_FULL  = ['일요일','월요일','화요일','수요일','목요일','금요일','토요일'];
const MONTH_KO = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

const DAY_OPTS = [
  {e:'MON',k:'월'},{e:'TUE',k:'화'},{e:'WED',k:'수'},
  {e:'THU',k:'목'},{e:'FRI',k:'금'},{e:'SAT',k:'토'},{e:'SUN',k:'일'},
];

function isoToHHMM(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function calcDep(arrIso, durMin) {
  const dep = new Date(new Date(arrIso).getTime() - durMin * 60000);
  return `${String(dep.getHours()).padStart(2,'0')}:${String(dep.getMinutes()).padStart(2,'0')}`;
}

function isToday(date) {
  const t = new Date();
  return date.getFullYear()===t.getFullYear()
    && date.getMonth()===t.getMonth()
    && date.getDate()===t.getDate();
}

function isSameDay(a, b) {
  return a.getFullYear()===b.getFullYear()
    && a.getMonth()===b.getMonth()
    && a.getDate()===b.getDate();
}

function getSchedulesFor(schedules, date) {
  const dow = DOW_ENG[date.getDay()];
  return schedules.filter(s => {
    if (s.routineRule?.daysOfWeek) return s.routineRule.daysOfWeek.includes(dow);
    return isSameDay(new Date(s.arrivalTime), date);
  });
}

function buildGrid(year, month) {
  const firstDow = new Date(year, month, 1).getDay();
  const dim      = new Date(year, month + 1, 0).getDate();
  const prevEnd  = new Date(year, month, 0).getDate();
  const cells    = [];
  for (let i = 0; i < firstDow; i++) {
    cells.push({ date: new Date(year, month - 1, prevEnd - firstDow + 1 + i), inMonth: false });
  }
  for (let d = 1; d <= dim; d++) {
    cells.push({ date: new Date(year, month, d), inMonth: true });
  }
  const target = Math.max(35, Math.ceil(cells.length / 7) * 7);
  let next = 1;
  while (cells.length < target) {
    cells.push({ date: new Date(year, month + 1, next++), inMonth: false });
  }
  return cells;
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
  title:'', originName:'', destinationName:'', arrivalTime:'09:00', daysOfWeek:[],
};

/* ── Nav tabs ─────────────────────────────────────────────────────── */
const NAV_TABS = [
  { label:'홈',     path:'/v3'          },
  { label:'캘린더', path:'/v3/calendar' },
  { label:'설정',   path:'/settings'    },
];

/* ── TopBar (desktop) ─────────────────────────────────────────────── */
function TopBar() {
  const navigate  = useNavigate();
  const { pathname } = useLocation();
  return (
    <header className="cv3-topbar">
      <span className="cv3-topbar__brand">오늘어디</span>
      <nav className="cv3-topbar__nav">
        {NAV_TABS.map(tab => (
          <button
            key={tab.path}
            className={`cv3-topbar__link${pathname === tab.path ? ' cv3-topbar__link--on' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
    </header>
  );
}

/* ── BottomTab (mobile) ───────────────────────────────────────────── */
function BottomTab() {
  const navigate  = useNavigate();
  const { pathname } = useLocation();
  return (
    <nav className="cv3-bottomtab">
      {NAV_TABS.map(tab => {
        const active = pathname === tab.path;
        return (
          <button
            key={tab.path}
            className={`cv3-bottomtab__item${active ? ' cv3-bottomtab__item--on' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            <span className="cv3-bottomtab__dot-wrap">
              {active && <span className="cv3-bottomtab__dot" />}
            </span>
            <span className="cv3-bottomtab__label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

/* ── SwipeItem ────────────────────────────────────────────────────── */
function SwipeItem({ onDelete, onTap, children }) {
  const [open, setOpen]   = useState(false);
  const startX  = useRef(null);
  const moved   = useRef(false);

  const onDown  = (e) => { startX.current = e.clientX; moved.current = false; };
  const onMove  = (e) => {
    if (startX.current === null) return;
    const dx = e.clientX - startX.current;
    if (Math.abs(dx) > 6) moved.current = true;
    if (dx < -24) setOpen(true);
    if (dx >  24) setOpen(false);
  };
  const onUp    = () => { startX.current = null; };
  const onClick = () => {
    if (moved.current) return;
    if (open) { setOpen(false); return; }
    onTap();
  };

  return (
    <div className="cv3-swipe-wrap">
      <button
        className="cv3-del-reveal"
        onClick={e => { e.stopPropagation(); onDelete(); }}
      >
        삭제
      </button>
      <div
        className={`cv3-swipe-inner${open ? ' cv3-swipe-inner--open' : ''}`}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerLeave={onUp}
        onClick={onClick}
      >
        {children}
      </div>
    </div>
  );
}

/* ── ScheduleItem ─────────────────────────────────────────────────── */
function ScheduleItem({ schedule, onTap, onDelete, last }) {
  const arrTime = isoToHHMM(schedule.arrivalTime);
  const depTime = calcDep(schedule.arrivalTime, schedule.averageDurationMinutes);
  const active  = schedule.routineRule?.daysOfWeek ?? [];

  return (
    <SwipeItem onDelete={onDelete} onTap={onTap}>
      <div className={`cv3-sched-item${last ? '' : ' cv3-sched-item--rule'}`}>
        <div className="cv3-sched-item__head">
          <span className="cv3-sched-item__title">{schedule.title}</span>
          <span className="cv3-sched-item__time">{arrTime}</span>
        </div>
        <div className="cv3-sched-item__route">
          {schedule.origin.name} → {schedule.destination.name}
        </div>
        <div className="cv3-sched-item__depart">
          {depTime} 출발 · {schedule.averageDurationMinutes}분 소요
        </div>
        {active.length > 0 && (
          <div className="cv3-sched-item__days">
            {DAY_OPTS.map(({ e, k }) => (
              <span
                key={e}
                className={`cv3-sched-item__day${active.includes(e) ? ' cv3-sched-item__day--on' : ''}`}
              >
                {k}
              </span>
            ))}
          </div>
        )}
      </div>
    </SwipeItem>
  );
}

/* ── AddEditSheet ─────────────────────────────────────────────────── */
function AddEditSheet({ mode, init, onSave, onCancel, onDelete }) {
  const [form, setForm]     = useState(init ?? EMPTY_FORM);
  const [confirmDel, setConfirmDel] = useState(false);

  const set      = (k, v) => setForm(f => ({ ...f, [k]: v }));
  const toggleDay = (e) => setForm(f => ({
    ...f,
    daysOfWeek: f.daysOfWeek.includes(e)
      ? f.daysOfWeek.filter(d => d !== e)
      : [...f.daysOfWeek, e],
  }));

  if (confirmDel) {
    return (
      <div className="cv3-overlay" onClick={() => setConfirmDel(false)}>
        <div className="cv3-confirm" onClick={e => e.stopPropagation()}>
          <p className="cv3-confirm__msg">삭제할까요?</p>
          <div className="cv3-confirm__btns">
            <button className="cv3-confirm__cancel" onClick={() => setConfirmDel(false)}>취소</button>
            <button className="cv3-confirm__del" onClick={onDelete}>삭제</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="cv3-overlay" onClick={onCancel}>
      <div className="cv3-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv3-sheet__handle" />

        {/* 일정 이름 */}
        <input
          className="cv3-sheet__field"
          placeholder="일정 이름"
          value={form.title}
          onChange={e => set('title', e.target.value)}
        />

        {/* 출발지 */}
        <div className="cv3-sheet__group">
          <input
            className="cv3-sheet__field"
            placeholder="출발지"
            value={form.originName}
            onChange={e => set('originName', e.target.value)}
          />
          <span className="cv3-sheet__hint">기본: 우이동</span>
        </div>

        {/* 도착지 */}
        <input
          className="cv3-sheet__field"
          placeholder="도착지"
          value={form.destinationName}
          onChange={e => set('destinationName', e.target.value)}
        />

        {/* 도착 시간 */}
        <div className="cv3-sheet__group">
          <input
            className="cv3-sheet__field cv3-sheet__field--time"
            type="time"
            value={form.arrivalTime}
            onChange={e => set('arrivalTime', e.target.value)}
          />
          <span className="cv3-sheet__hint">도착 희망 시간</span>
        </div>

        {/* 반복 요일 */}
        <div className="cv3-sheet__repeat-block">
          <span className="cv3-sheet__repeat-label">반복</span>
          <div className="cv3-sheet__daybtn-row">
            {DAY_OPTS.map(({ e, k }) => (
              <button
                key={e}
                className={`cv3-sheet__daybtn${form.daysOfWeek.includes(e) ? ' cv3-sheet__daybtn--on' : ''}`}
                onClick={() => toggleDay(e)}
              >
                {k}
              </button>
            ))}
          </div>
        </div>

        {/* 하단 버튼 */}
        <div className="cv3-sheet__footer">
          <button className="cv3-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv3-sheet__save" onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정'}
          </button>
        </div>

        {/* 삭제 (수정 모드만) */}
        {mode === 'edit' && (
          <button className="cv3-sheet__delete-text" onClick={() => setConfirmDel(true)}>
            이 일정 삭제
          </button>
        )}
      </div>
    </div>
  );
}

/* ── DeleteConfirm (swipe에서 호출) ──────────────────────────────── */
function DeleteConfirm({ onConfirm, onCancel }) {
  return (
    <div className="cv3-overlay" onClick={onCancel}>
      <div className="cv3-confirm" onClick={e => e.stopPropagation()}>
        <p className="cv3-confirm__msg">삭제할까요?</p>
        <div className="cv3-confirm__btns">
          <button className="cv3-confirm__cancel" onClick={onCancel}>취소</button>
          <button className="cv3-confirm__del" onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   CalendarV3
   ================================================================ */
export default function CalendarV3() {
  const today = new Date();

  const [schedules,    setSchedules]    = useState(mockScheduleList.data);
  const [selectedDate, setSelectedDate] = useState(today);
  const [viewYear,     setViewYear]     = useState(today.getFullYear());
  const [viewMonth,    setViewMonth]    = useState(today.getMonth());
  const [detailVis,    setDetailVis]    = useState(true);
  const [calFade,      setCalFade]      = useState(false);
  const [sheetState,   setSheetState]   = useState(null); // null | {mode,scheduleId?,init?}
  const [deleteConfirm, setDeleteConfirm] = useState(null); // null | scheduleId

  const daySched = getSchedulesFor(schedules, selectedDate);
  const cells    = buildGrid(viewYear, viewMonth);

  /* 날짜 선택 */
  const handleDateSelect = (date) => {
    if (isSameDay(date, selectedDate)) return;
    setDetailVis(false);
    setTimeout(() => { setSelectedDate(date); setDetailVis(true); }, 200);
  };

  /* 월 변경 */
  const handleMonthChange = (delta) => {
    setCalFade(true);
    setTimeout(() => {
      let m = viewMonth + delta, y = viewYear;
      if (m < 0)  { m = 11; y--; }
      if (m > 11) { m = 0;  y++; }
      setViewMonth(m);
      setViewYear(y);
      setCalFade(false);
    }, 200);
  };

  /* 추가 저장 */
  const handleAddSave = (form) => {
    const [h, min] = form.arrivalTime.split(':');
    const arr = new Date(selectedDate);
    arr.setHours(parseInt(h,10), parseInt(min,10), 0, 0);
    setSchedules(prev => [...prev, {
      scheduleId: `sch_${Date.now()}`,
      title: form.title || '새 일정',
      origin:      { name: form.originName      || '출발지' },
      destination: { name: form.destinationName || '도착지' },
      arrivalTime: arr.toISOString(),
      reminderAt:  arr.toISOString(),
      averageDurationMinutes: 30,
      routineRule: form.daysOfWeek.length > 0
        ? { type:'WEEKLY', daysOfWeek: form.daysOfWeek } : null,
    }]);
    setSheetState(null);
  };

  /* 수정 저장 */
  const handleEditSave = (form) => {
    const { scheduleId } = sheetState;
    setSchedules(prev => prev.map(s => {
      if (s.scheduleId !== scheduleId) return s;
      const [h, min] = form.arrivalTime.split(':');
      const arr = new Date(s.arrivalTime);
      arr.setHours(parseInt(h,10), parseInt(min,10), 0, 0);
      return {
        ...s,
        title:       form.title       || s.title,
        origin:      { name: form.originName      || s.origin.name },
        destination: { name: form.destinationName || s.destination.name },
        arrivalTime: arr.toISOString(),
        routineRule: form.daysOfWeek.length > 0
          ? { type:'WEEKLY', daysOfWeek: form.daysOfWeek } : null,
      };
    }));
    setSheetState(null);
  };

  /* 삭제 */
  const handleDelete = (id) => {
    setSchedules(prev => prev.filter(s => s.scheduleId !== id));
    setSheetState(null);
    setDeleteConfirm(null);
  };

  /* 캘린더 그리드 JSX */
  const calendarGrid = (
    <>
      <section className="cv3-month-header">
        <button className="cv3-month-arrow" onClick={() => handleMonthChange(-1)}>‹</button>
        <div className="cv3-month-center">
          <span className="cv3-month-name">{MONTH_KO[viewMonth]}</span>
          <span className="cv3-month-year">{viewYear}</span>
        </div>
        <button className="cv3-month-arrow" onClick={() => handleMonthChange(1)}>›</button>
      </section>

      <section className="cv3-cal-section">
        <div className="cv3-dow-row">
          {DOW_KO.map((d, i) => (
            <div
              key={d}
              className={`cv3-dow${i===0 ? ' cv3-dow--sun' : i===6 ? ' cv3-dow--sat' : ''}`}
            >
              {d}
            </div>
          ))}
        </div>

        <div className={`cv3-grid${calFade ? ' cv3-grid--fade' : ''}`}>
          {cells.map(({ date, inMonth }, idx) => {
            if (!inMonth) return <div key={idx} className="cv3-cell cv3-cell--empty" />;
            const tod    = isToday(date);
            const sel    = isSameDay(date, selectedDate);
            const hasSch = getSchedulesFor(schedules, date).length > 0;
            const dow    = date.getDay();
            return (
              <div key={idx} className="cv3-cell" onClick={() => handleDateSelect(date)}>
                <span className={[
                  'cv3-num',
                  tod             ? 'cv3-num--today' : '',
                  sel && !tod     ? 'cv3-num--sel'   : '',
                  !hasSch && !tod ? 'cv3-num--dim'   : '',
                  dow===0 && !tod ? 'cv3-num--sun'   : '',
                  dow===6 && !tod ? 'cv3-num--sat'   : '',
                ].filter(Boolean).join(' ')}>
                  {date.getDate()}
                </span>
                {tod && <span className="cv3-today-dot" />}
                {sel && !tod && <span className="cv3-sel-line" />}
              </div>
            );
          })}
        </div>
      </section>
    </>
  );

  return (
    <div className="cv3">
      <TopBar />

      <div className="cv3-body">
        <div className="cv3-layout">

          {/* ── 왼쪽: 캘린더 ── */}
          <div className="cv3-layout__left">
            {calendarGrid}
          </div>

          {/* ── 오른쪽: 일정 상세 ── */}
          <div className="cv3-layout__right">
            {/* 모바일 전용 구분선 */}
            <div className="cv3-rule cv3-rule--mobile" />

        {/* ── 일정 상세 ── */}
        <section className={`cv3-detail${!detailVis ? ' cv3-detail--fade' : ''}`}>

          {/* 날짜 히어로 */}
          <div className="cv3-date-hero">
            <span className="cv3-date-hero__num">{selectedDate.getDate()}</span>
            <span className="cv3-date-hero__wd">{WD_FULL[selectedDate.getDay()]}</span>
          </div>

          {/* 일정 리스트 or 빈 상태 */}
          {daySched.length > 0 ? (
            <div className="cv3-sched-list">
              {daySched.map((s, i) => (
                <ScheduleItem
                  key={s.scheduleId}
                  schedule={s}
                  last={i === daySched.length - 1}
                  onTap={() => setSheetState({
                    mode:'edit',
                    scheduleId: s.scheduleId,
                    init: scheduleToForm(s),
                  })}
                  onDelete={() => setDeleteConfirm(s.scheduleId)}
                />
              ))}
            </div>
          ) : (
            <p className="cv3-empty-msg">일정이 없는 날이에요</p>
          )}

          {/* + 새 일정 버튼 */}
          <button className="cv3-add-btn" onClick={() => setSheetState({ mode:'add' })}>
            + 새 일정
          </button>

          <div className="cv3-detail-spacer" />
        </section>
          </div>{/* cv3-layout__right */}

        </div>{/* cv3-layout */}
      </div>{/* cv3-body */}

      <BottomTab />

      {/* ── 바텀시트 ── */}
      {sheetState && (
        <AddEditSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={sheetState.mode === 'add' ? handleAddSave : handleEditSave}
          onCancel={() => setSheetState(null)}
          onDelete={() => handleDelete(sheetState.scheduleId)}
        />
      )}

      {/* ── 스와이프 삭제 확인 ── */}
      {deleteConfirm && (
        <DeleteConfirm
          onConfirm={() => handleDelete(deleteConfirm)}
          onCancel={() => setDeleteConfirm(null)}
        />
      )}
    </div>
  );
}
