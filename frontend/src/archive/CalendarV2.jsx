import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { mockScheduleList } from '../data/mockData';
import './CalendarV2.css';

/* ── Helpers ─────────────────────────────────────────────────────── */
const DOW_ENG = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const DOW_KO  = ['일', '월', '화', '수', '목', '금', '토'];
const WD_FULL = ['일요일','월요일','화요일','수요일','목요일','금요일','토요일'];

function isoToHHMM(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function calcDep(arrIso, durMin) {
  const dep = new Date(new Date(arrIso).getTime() - durMin * 60000);
  return `${String(dep.getHours()).padStart(2,'0')}:${String(dep.getMinutes()).padStart(2,'0')}`;
}

function fmtDateKo(date) {
  return `${date.getMonth()+1}월 ${date.getDate()}일 ${WD_FULL[date.getDay()]}`;
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
  const cells = [];

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

const EMPTY_FORM = { title:'', originName:'', destinationName:'', arrivalTime:'09:00', daysOfWeek:[] };

const DAY_OPTS = [
  {e:'MON',k:'월'},{e:'TUE',k:'화'},{e:'WED',k:'수'},
  {e:'THU',k:'목'},{e:'FRI',k:'금'},{e:'SAT',k:'토'},{e:'SUN',k:'일'},
];

/* ── ScheduleCard ─────────────────────────────────────────────────── */
function ScheduleCard({ schedule, onEdit, onDelete }) {
  const arrTime = isoToHHMM(schedule.arrivalTime);
  const depTime = calcDep(schedule.arrivalTime, schedule.averageDurationMinutes);
  const active  = schedule.routineRule?.daysOfWeek ?? [];

  return (
    <div className="cv2-scard">
      <div className="cv2-scard__head">
        <span className="cv2-scard__name">{schedule.title}</span>
        <span className="cv2-scard__arr">{arrTime}</span>
      </div>
      <div className="cv2-scard__route">{schedule.origin.name} → {schedule.destination.name}</div>
      <div className="cv2-scard__depart">
        {depTime} 출발 · {schedule.averageDurationMinutes}분 소요
      </div>
      {active.length > 0 && (
        <div className="cv2-scard__days">
          {DAY_OPTS.map(({ e, k }) => (
            <span key={e} className={`cv2-scard__day${active.includes(e) ? ' cv2-scard__day--on' : ''}`}>{k}</span>
          ))}
        </div>
      )}
      <div className="cv2-scard__btns">
        <button className="cv2-scard__edit" onClick={() => onEdit(schedule)}>수정</button>
        <button className="cv2-scard__del"  onClick={() => onDelete(schedule.scheduleId)}>삭제</button>
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
    <div className="cv2-overlay" onClick={onCancel}>
      <div className="cv2-sheet" onClick={e => e.stopPropagation()}>
        <div className="cv2-sheet__handle" />
        <div className="cv2-sheet__heading">{mode === 'add' ? '새 일정 추가' : '일정 수정'}</div>

        <input
          className="cv2-sheet__input"
          placeholder="일정 이름"
          value={form.title}
          onChange={e => set('title', e.target.value)}
        />

        <div className="cv2-sheet__field">
          <input
            className="cv2-sheet__input cv2-sheet__input--icon"
            placeholder="출발지 검색"
            value={form.originName}
            onChange={e => set('originName', e.target.value)}
          />
          <Search size={16} className="cv2-sheet__search-icon" />
        </div>

        <div className="cv2-sheet__field">
          <input
            className="cv2-sheet__input cv2-sheet__input--icon"
            placeholder="도착지 검색"
            value={form.destinationName}
            onChange={e => set('destinationName', e.target.value)}
          />
          <Search size={16} className="cv2-sheet__search-icon" />
        </div>

        <div className="cv2-sheet__timewrap">
          <input
            className="cv2-sheet__time"
            type="time"
            value={form.arrivalTime}
            onChange={e => set('arrivalTime', e.target.value)}
          />
        </div>
        <div className="cv2-sheet__hint">도착 시간을 입력하면 출발 시간을 자동으로 계산해요</div>

        <div className="cv2-sheet__label">반복 요일</div>
        <div className="cv2-sheet__daybtn-row">
          {DAY_OPTS.map(({ e, k }) => (
            <button
              key={e}
              className={`cv2-sheet__daybtn${form.daysOfWeek.includes(e) ? ' cv2-sheet__daybtn--on' : ''}`}
              onClick={() => toggleDay(e)}
            >
              {k}
            </button>
          ))}
        </div>

        <div className="cv2-sheet__footer">
          <button className="cv2-sheet__cancel" onClick={onCancel}>취소</button>
          <button className="cv2-sheet__save" onClick={() => onSave(form)}>
            {mode === 'add' ? '저장' : '수정 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── DeleteDialog ─────────────────────────────────────────────────── */
function DeleteDialog({ onConfirm, onCancel }) {
  return (
    <div className="cv2-overlay cv2-overlay--center" onClick={onCancel}>
      <div className="cv2-dialog" onClick={e => e.stopPropagation()}>
        <div className="cv2-dialog__text">이 일정을 삭제할까요?</div>
        <div className="cv2-dialog__btns">
          <button className="cv2-dialog__cancel" onClick={onCancel}>취소</button>
          <button className="cv2-dialog__del"    onClick={onConfirm}>삭제</button>
        </div>
      </div>
    </div>
  );
}

/* ── CalendarV2 ───────────────────────────────────────────────────── */
const MONTH_KO = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

export default function CalendarV2() {
  const navigate = useNavigate();
  const today    = new Date();

  const [schedules,     setSchedules]     = useState(mockScheduleList.data);
  const [selectedDate,  setSelectedDate]  = useState(today);
  const [viewYear,      setViewYear]      = useState(today.getFullYear());
  const [viewMonth,     setViewMonth]     = useState(today.getMonth());
  const [aiVisible,     setAiVisible]     = useState(true);
  const [calFade,       setCalFade]       = useState(false);
  const [sheetState,    setSheetState]    = useState(null);
  const [deleteId,      setDeleteId]      = useState(null);

  const daySched  = getSchedulesFor(schedules, selectedDate);
  const cells     = buildGrid(viewYear, viewMonth);
  const todayFlag = isToday(selectedDate);

  const nextDep = todayFlag && daySched.length > 0
    ? [...daySched]
        .map(s => calcDep(s.arrivalTime, s.averageDurationMinutes))
        .sort()[0]
    : null;

  const aiMsg = todayFlag
    ? daySched.length > 0
      ? `오늘 일정이 ${daySched.length}개 있어요. 다음 출발은 ${nextDep}이에요.`
      : '오늘 등록된 일정이 없어요. 추가해볼까요?'
    : daySched.length > 0
      ? `${fmtDateKo(selectedDate)}에는 일정이 ${daySched.length}개 있어요.`
      : `${fmtDateKo(selectedDate)}에는 등록된 일정이 없어요. 추가해볼까요?`;

  const handleDateSelect = (date) => {
    if (isSameDay(date, selectedDate)) return;
    setAiVisible(false);
    setTimeout(() => { setSelectedDate(date); setAiVisible(true); }, 150);
  };

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

  const handleAddSave = (form) => {
    const [h, min] = form.arrivalTime.split(':');
    const arr = new Date(selectedDate);
    arr.setHours(parseInt(h, 10), parseInt(min, 10), 0, 0);
    setSchedules(prev => [...prev, {
      scheduleId: `sch_${Date.now()}`,
      title: form.title || '새 일정',
      origin:      { name: form.originName      || '출발지' },
      destination: { name: form.destinationName || '도착지' },
      arrivalTime: arr.toISOString(),
      reminderAt:  arr.toISOString(),
      averageDurationMinutes: 30,
      routineRule: form.daysOfWeek.length > 0
        ? { type: 'WEEKLY', daysOfWeek: form.daysOfWeek }
        : null,
    }]);
    setSheetState(null);
  };

  const handleEditSave = (form) => {
    const { scheduleId } = sheetState;
    setSchedules(prev => prev.map(s => {
      if (s.scheduleId !== scheduleId) return s;
      const [h, min] = form.arrivalTime.split(':');
      const arr = new Date(s.arrivalTime);
      arr.setHours(parseInt(h, 10), parseInt(min, 10), 0, 0);
      return {
        ...s,
        title:       form.title       || s.title,
        origin:      { name: form.originName      || s.origin.name },
        destination: { name: form.destinationName || s.destination.name },
        arrivalTime: arr.toISOString(),
        routineRule: form.daysOfWeek.length > 0
          ? { type: 'WEEKLY', daysOfWeek: form.daysOfWeek }
          : null,
      };
    }));
    setSheetState(null);
  };

  const handleDeleteConfirm = () => {
    setSchedules(prev => prev.filter(s => s.scheduleId !== deleteId));
    setDeleteId(null);
  };

  return (
    <div className="cv2-page">

      {/* ── Header ── */}
      <header className="cv2-header">
        <div className="cv2-header__left">
          <div className="cv2-header__avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="9" fill="white" fillOpacity="0.9"/>
              <path d="M12 8v4l2.5 2.5" stroke="#0F6E56" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <div>
            <div className="cv2-header__name">출근길 AI</div>
            <div className="cv2-header__status">
              <span className="cv2-header__dot" />
              캘린더
            </div>
          </div>
        </div>
        <div className="cv2-month-badge">{viewYear}.{viewMonth + 1}</div>
      </header>

      {/* ── Scrollable body ── */}
      <div className="cv2-scroll">

        {/* ── Calendar ── */}
        <section className="cv2-cal">
          <div className="cv2-cal__nav">
            <button className="cv2-cal__arrow" onClick={() => handleMonthChange(-1)}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2.2"
                  strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            <span className="cv2-cal__title">{MONTH_KO[viewMonth]} {viewYear}</span>
            <button className="cv2-cal__arrow" onClick={() => handleMonthChange(1)}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M9 18l6-6-6-6" stroke="currentColor" strokeWidth="2.2"
                  strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
          </div>

          {/* Day-of-week headers */}
          <div className="cv2-cal__dow-row">
            {DOW_KO.map((d, i) => (
              <div
                key={d}
                className={`cv2-cal__dow${i === 0 ? ' cv2-cal__dow--sun' : i === 6 ? ' cv2-cal__dow--sat' : ''}`}
              >
                {d}
              </div>
            ))}
          </div>

          {/* Date grid */}
          <div className={`cv2-cal__grid${calFade ? ' cv2-cal__grid--fade' : ''}`}>
            {cells.map(({ date, inMonth }, idx) => {
              const sel      = isSameDay(date, selectedDate);
              const tod      = isToday(date);
              const dotCount = inMonth ? Math.min(getSchedulesFor(schedules, date).length, 2) : 0;
              const dow      = date.getDay();
              return (
                <div
                  key={idx}
                  className={[
                    'cv2-cal__cell',
                    !inMonth ? 'cv2-cal__cell--out' : '',
                    dow === 0 ? 'cv2-cal__cell--sun' : dow === 6 ? 'cv2-cal__cell--sat' : '',
                  ].join(' ').trim()}
                  onClick={() => inMonth && handleDateSelect(date)}
                >
                  <span className={[
                    'cv2-cal__num',
                    tod ? 'cv2-cal__num--today' : sel ? 'cv2-cal__num--sel' : '',
                  ].join(' ').trim()}>
                    {date.getDate()}
                  </span>
                  {dotCount > 0 && (
                    <div className="cv2-cal__dots">
                      {Array.from({ length: dotCount }).map((_, i) => (
                        <span key={i} className="cv2-cal__dot" />
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>

        {/* ── AI section ── */}
        <section className={`cv2-ai${!aiVisible ? ' cv2-ai--fade' : ''}`}>

          {/* Bubble 1 */}
          <div className="cv2-row">
            <div className="cv2-mini-avatar" />
            <div className="cv2-bubble">{aiMsg}</div>
          </div>

          {/* Schedule cards */}
          {daySched.length > 0 && (
            <div className="cv2-cards">
              {daySched.map(s => (
                <ScheduleCard
                  key={s.scheduleId}
                  schedule={s}
                  onEdit={sc => setSheetState({
                    mode: 'edit',
                    scheduleId: sc.scheduleId,
                    init: scheduleToForm(sc),
                  })}
                  onDelete={id => setDeleteId(id)}
                />
              ))}
            </div>
          )}

          {/* Bubble 2 — only when schedules exist */}
          {daySched.length > 0 && (
            <div className="cv2-row">
              <div className="cv2-mini-avatar" />
              <div className="cv2-bubble">새 일정을 추가할까요?</div>
            </div>
          )}

          {/* Add button */}
          <div className="cv2-add-wrap">
            <button className="cv2-add-btn" onClick={() => setSheetState({ mode: 'add' })}>
              + 새 일정 추가
            </button>
          </div>

          <div className="cv2-spacer" />
        </section>
      </div>

      {/* ── Bottom nav ── */}
      <nav className="cv2-nav">
        <button className="cv2-nav__tab" onClick={() => navigate('/v2')}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span>AI</span>
        </button>
        <button className="cv2-nav__tab cv2-nav__tab--active" onClick={() => navigate('/v2/calendar')}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
            <path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          <span>캘린더</span>
        </button>
        <button className="cv2-nav__tab" onClick={() => navigate('/settings')}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2"/>
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"
              stroke="currentColor" strokeWidth="2"/>
          </svg>
          <span>설정</span>
        </button>
      </nav>

      {/* ── Add / Edit sheet ── */}
      {sheetState && (
        <AddEditSheet
          mode={sheetState.mode}
          init={sheetState.init}
          onSave={sheetState.mode === 'add' ? handleAddSave : handleEditSave}
          onCancel={() => setSheetState(null)}
        />
      )}

      {/* ── Delete dialog ── */}
      {deleteId && (
        <DeleteDialog
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteId(null)}
        />
      )}
    </div>
  );
}
