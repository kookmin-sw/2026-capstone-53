import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { CalendarSkeletons, ErrorState } from '../components/StateUI';
import './Calendar.css';

/* ================================================================
   상수 & 유틸
   ================================================================ */

const DAY_LABELS  = ['일', '월', '화', '수', '목', '금', '토'];
const DAY_KEYS    = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const DAY_SHORT   = { SUN: '일', MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토' };
const DAY_NUM     = { SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6 };

function getCalendarDays(year, month) {
  const firstDow    = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const days = Array(firstDow).fill(null);
  for (let d = 1; d <= daysInMonth; d++) days.push(d);
  while (days.length % 7 !== 0) days.push(null);
  return days;
}

function scheduleActiveOnDate(sch, year, month, day) {
  const days = sch.repeatDays ?? sch.routineRule?.daysOfWeek ?? [];
  if (days.length === 0) return true; // 단발성 일정 — 모든 날에 표시
  const target = new Date(year, month, day);
  if (sch.startDate) {
    const [sy, sm, sd] = sch.startDate.split('-').map(Number);
    if (target < new Date(sy, sm - 1, sd)) return false;
  }
  if (sch.endDate) {
    const [ey, em, ed] = sch.endDate.split('-').map(Number);
    if (target > new Date(ey, em - 1, ed)) return false;
  }
  const dow = target.getDay();
  return days.some(d => DAY_NUM[d] === dow);
}

function getSchedulesForDate(schedules, year, month, day) {
  return schedules.filter(s => scheduleActiveOnDate(s, year, month, day));
}

function hasSchedule(schedules, year, month, day) {
  return schedules.some(s => scheduleActiveOnDate(s, year, month, day));
}

function padTwo(n) { return String(n).padStart(2, '0'); }

/* ================================================================
   PlaceSearchOverlay — 장소 검색 (mock geocode / 실제 API 교체 가능)
   ================================================================ */

function PlaceSearchOverlay({ field, onSelect, onClose }) {
  const [query, setQuery]     = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [closing, setClosing]   = useState(false);
  const timerRef  = useRef(null);
  const inputRef  = useRef(null);

  useEffect(() => { inputRef.current?.focus(); }, []);

  const dismiss = useCallback((cb) => {
    setClosing(true);
    setTimeout(cb, 200);
  }, []);

  const handleClose  = () => dismiss(onClose);
  const handleSelect = (place) => dismiss(() => onSelect(place));

  const runSearch = useCallback(async (q) => {
    if (!q.trim()) {
      setResults([]);
      setLoading(false);
      setSearched(false);
      setSearchError('');
      return;
    }
    setLoading(true);
    setSearchError('');
    try {
      const data = await api.geocode.search({ query: q });
      setResults(data.matched ? [data] : []);
      setSearched(true);
    } catch (err) {
      if (err.code === 'GEOCODE_NO_MATCH') {
        setResults([]);
        setSearched(true);
      } else {
        setSearchError('검색 중 문제가 발생했어요');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const handleChange = (e) => {
    const q = e.target.value;
    setQuery(q);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => runSearch(q), 500);
  };

  const clearQuery = () => {
    setQuery('');
    setResults([]);
    setSearched(false);
    inputRef.current?.focus();
  };

  const title = field === 'origin' ? '출발지 검색' : '도착지 검색';

  return (
    <div className={`place-overlay${closing ? ' place-overlay--closing' : ''}`}>
      <div className="place-overlay__bar">
        <button className="place-overlay__back" onClick={handleClose} aria-label="닫기">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M19 12H5M5 12L12 19M5 12L12 5" stroke="currentColor" strokeWidth="2"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <span className="place-overlay__title">{title}</span>
      </div>

      <div className="place-overlay__search-row">
        <div className="place-overlay__search-wrap">
          <svg className="place-overlay__search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2"/>
            <path d="M21 21l-4-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          <input
            ref={inputRef}
            className="place-overlay__input"
            type="text"
            placeholder="장소 또는 주소 검색"
            value={query}
            onChange={handleChange}
          />
          {query && (
            <button className="place-overlay__clear" onClick={clearQuery} aria-label="지우기">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="9" fill="#C0BAB4"/>
                <path d="M9 9l6 6M15 9l-6 6" stroke="white" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </button>
          )}
        </div>
      </div>

      <div className="place-overlay__results">
        {loading && (
          <div className="place-overlay__status">
            <div className="place-overlay__spinner" />
            검색 중...
          </div>
        )}
        {!loading && searchError && (
          <div className="place-overlay__status">{searchError}</div>
        )}
        {!loading && !searchError && searched && results.length === 0 && (
          <div className="place-overlay__status">검색 결과가 없어요</div>
        )}
        {!loading && !query && !searchError && (
          <div className="place-overlay__hint">장소명, 주소, 건물명으로 검색하세요</div>
        )}
        {!loading && results.map(place => (
          <button key={place.placeId} className="place-result" onClick={() => handleSelect(place)}>
            <div className="place-result__name">{place.name}</div>
            <div className="place-result__addr">{place.address}</div>
          </button>
        ))}
      </div>
    </div>
  );
}

/* ================================================================
   iOS-style Wheel Picker
   ================================================================ */

const TPW_H    = 32;  // 항목 높이
const TPW_PAD  = 68;  // 미사용 (하위 호환)
const ITPW_PAD = 34;  // 인라인 피커 패딩 — (100 - 32) / 2 = 34, 가운데 정렬용

function TimeWheelCol({ count, selected, onSelect, colRef, padHeight = TPW_PAD }) {
  const pad   = n => String(n).padStart(2, '0');
  const timer = useRef(null);

  useEffect(() => {
    if (colRef.current) colRef.current.scrollTop = selected * TPW_H;
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleScroll = () => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      if (!colRef.current) return;
      const idx = Math.round(colRef.current.scrollTop / TPW_H);
      onSelect(Math.max(0, Math.min(count - 1, idx)));
    }, 60);
  };

  const scrollTo = idx => {
    onSelect(idx);
    colRef.current?.scrollTo({ top: idx * TPW_H, behavior: 'smooth' });
  };

  return (
    <div className="tpw__col" ref={colRef} onScroll={handleScroll}>
      <div style={{ height: padHeight, flexShrink: 0 }} />
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          className={`tpw__item${i === selected ? ' tpw__item--sel' : ''}`}
          onClick={() => scrollTo(i)}
        >
          {pad(i)}
        </div>
      ))}
      <div style={{ height: padHeight, flexShrink: 0 }} />
    </div>
  );
}

/* 인라인 휠 피커 — 입력란 바로 아래 펼쳐지는 형식 */
function InlineTimePicker({ value, onChange }) {
  const pad   = n => String(n).padStart(2, '0');
  const initH = value ? parseInt(value.split(':')[0], 10) : 9;
  const initM = value ? parseInt(value.split(':')[1], 10) : 0;
  const [selH, setSelH] = useState(initH);
  const [selM, setSelM] = useState(initM);
  const hourRef = useRef(null);
  const minRef  = useRef(null);

  const handleH = h => { setSelH(h); onChange(`${pad(h)}:${pad(selM)}`); };
  const handleM = m => { setSelM(m); onChange(`${pad(selH)}:${pad(m)}`); };

  return (
    <div className="itpw">
      <div className="itpw__highlight" />
      <div className="itpw__fade itpw__fade--top" />
      <div className="itpw__fade itpw__fade--bot" />
      <TimeWheelCol count={24} selected={selH} onSelect={handleH} colRef={hourRef} padHeight={ITPW_PAD} />
      <div className="itpw__sep">:</div>
      <TimeWheelCol count={60} selected={selM} onSelect={handleM} colRef={minRef} padHeight={ITPW_PAD} />
    </div>
  );
}

/* ================================================================
   BottomSheet — 일정 추가 / 수정
   ================================================================ */

const EMPTY_FORM = {
  title: '',
  originName: '',
  originPlace: null,        // { name, lat, lng, address, placeId, provider }
  destinationName: '',
  destinationPlace: null,   // same
  arrivalTime: '09:00',
  usualDepartureTime: '07:30',
  repeatDays: [],
};

function BottomSheet({ editingSchedule, onClose, onSave, isSaving }) {
  const [form, setForm]             = useState(EMPTY_FORM);
  const [placeField, setPlaceField] = useState(null);    // 'origin' | 'destination' | null
  const [activePicker, setActivePicker] = useState(null); // 'usualDepartureTime' | 'arrivalTime' | null
  const [formError, setFormError]   = useState('');
  const sheetRef = useRef(null);

  useEffect(() => {
    if (editingSchedule) {
      setForm({
        title:              editingSchedule.title              || '',
        originName:         editingSchedule.originName         || '',
        originPlace:        editingSchedule.originPlace        || null,
        destinationName:    editingSchedule.destinationName    || '',
        destinationPlace:   editingSchedule.destinationPlace   || null,
        arrivalTime:        editingSchedule.arrivalTime        || '09:00',
        usualDepartureTime: editingSchedule.usualDepartureTime || '',
        repeatDays:         [...(editingSchedule.repeatDays    || [])],
      });
    } else {
      setForm(EMPTY_FORM);
    }
  }, [editingSchedule]);

  // 바텀시트 열릴 때 스크롤 잠금
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, []);

  const set = (key, val) => setForm(f => ({ ...f, [key]: val }));

  const toggleDay = (day) =>
    set('repeatDays', form.repeatDays.includes(day)
      ? form.repeatDays.filter(d => d !== day)
      : [...form.repeatDays, day]);

  const setAllDays = () => set('repeatDays', [...DAY_KEYS]);
  const setWeekdays = () => set('repeatDays', ['MON', 'TUE', 'WED', 'THU', 'FRI']);

  const fillCurrentLocation = (field) => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      const nameKey  = field === 'origin' ? 'originName'   : 'destinationName';
      const placeKey = field === 'origin' ? 'originPlace'  : 'destinationPlace';
      const place = {
        name: '현재 위치',
        lat:  coords.latitude,
        lng:  coords.longitude,
        address: '',
        placeId: null,
        provider: 'GPS',
      };
      setForm(f => ({ ...f, [nameKey]: place.name, [placeKey]: place }));
    });
  };

  const handlePlaceSelect = (place) => {
    // place: { name, lat, lng, address, placeId, provider }
    const nameKey  = placeField === 'origin' ? 'originName'  : 'destinationName';
    const placeKey = placeField === 'origin' ? 'originPlace' : 'destinationPlace';
    setForm(f => ({ ...f, [nameKey]: place.name, [placeKey]: place }));
    setPlaceField(null);
  };

  const handleSave = () => {
    if (!form.title.trim()) { setFormError('일정 이름을 입력해주세요'); return; }
    setFormError('');
    onSave(form);
  };

  const isEditing = !!editingSchedule;

  return (
    <>
      <div className="sheet-backdrop" onClick={onClose} />
      <div className="sheet" ref={sheetRef}>
        <div className="sheet__handle" />
        <div className="sheet__header">
          <h3 className="sheet__title">{isEditing ? '일정 수정' : '새 일정 추가'}</h3>
        </div>

        <div className="sheet__body">
          {/* 일정 이름 */}
          <div className="sf">
            <label className="sf__label">일정 이름</label>
            <input
              className="sf__input"
              type="text"
              placeholder="일정 이름 (예: 학교 수업)"
              value={form.title}
              onChange={e => set('title', e.target.value)}
            />
          </div>

          {/* 출발지 */}
          <div className="sf">
            <label className="sf__label">출발지</label>
            <div className="sf__place-row" onClick={() => setPlaceField('origin')}>
              <span className={form.originName ? 'sf__place-val' : 'sf__place-placeholder'}>
                {form.originName || '출발지를 검색하세요'}
              </span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <circle cx="11" cy="11" r="7" stroke="#A09A93" strokeWidth="2"/>
                <path d="M21 21l-4-4" stroke="#A09A93" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <button className="sf__loc-btn" onClick={() => fillCurrentLocation('origin')}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="4" fill="#0A7AFF"/>
                <circle cx="12" cy="12" r="9" stroke="#0A7AFF" strokeWidth="2" fill="none"/>
              </svg>
              현재 위치 사용
            </button>
          </div>

          {/* 도착지 */}
          <div className="sf">
            <label className="sf__label">도착지</label>
            <div className="sf__place-row" onClick={() => setPlaceField('destination')}>
              <span className={form.destinationName ? 'sf__place-val' : 'sf__place-placeholder'}>
                {form.destinationName || '도착지를 검색하세요'}
              </span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <circle cx="11" cy="11" r="7" stroke="#A09A93" strokeWidth="2"/>
                <path d="M21 21l-4-4" stroke="#A09A93" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <button className="sf__loc-btn" onClick={() => fillCurrentLocation('destination')}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="4" fill="#0A7AFF"/>
                <circle cx="12" cy="12" r="9" stroke="#0A7AFF" strokeWidth="2" fill="none"/>
              </svg>
              현재 위치 사용
            </button>
          </div>

          {/* 시간 입력 — 2열 나란히 */}
          <div className="sf sf--time-row">
            {/* 평소 출발 시간 */}
            <div className="sf__time-col">
              <label className="sf__label">평소 출발 시간</label>
              <div
                className="sf__time-wrap"
                onClick={() => setActivePicker(p => p === 'usualDepartureTime' ? null : 'usualDepartureTime')}
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
                  <path d="M12 7v5l3 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                </svg>
                <span className={`sf__time-val${form.usualDepartureTime ? ' sf__time-val--set' : ''}`}>
                  {form.usualDepartureTime || '시간 선택'}
                </span>
                <svg
                  className={`sf__time-chevron${activePicker === 'usualDepartureTime' ? ' sf__time-chevron--open' : ''}`}
                  width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2"
                    strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <div className={`sf__picker-wrap${activePicker === 'usualDepartureTime' ? ' sf__picker-wrap--open' : ''}`}>
                <InlineTimePicker
                  value={form.usualDepartureTime}
                  onChange={val => set('usualDepartureTime', val)}
                />
              </div>
            </div>

            {/* 도착 희망 시간 */}
            <div className="sf__time-col">
              <label className="sf__label">도착 희망 시간</label>
              <div
                className="sf__time-wrap"
                onClick={() => setActivePicker(p => p === 'arrivalTime' ? null : 'arrivalTime')}
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
                  <path d="M12 7v5l3 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                </svg>
                <span className={`sf__time-val${form.arrivalTime ? ' sf__time-val--set' : ''}`}>
                  {form.arrivalTime || '시간 선택'}
                </span>
                <svg
                  className={`sf__time-chevron${activePicker === 'arrivalTime' ? ' sf__time-chevron--open' : ''}`}
                  width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2"
                    strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <div className={`sf__picker-wrap${activePicker === 'arrivalTime' ? ' sf__picker-wrap--open' : ''}`}>
                <InlineTimePicker
                  value={form.arrivalTime}
                  onChange={val => set('arrivalTime', val)}
                />
              </div>
            </div>
          </div>

          {/* 반복 요일 */}
          <div className="sf">
            <div className="sf__row-label">
              <label className="sf__label">반복 요일</label>
              <div className="sf__quick-btns">
                <button className="sf__quick" onClick={setWeekdays}>평일</button>
                <button className="sf__quick" onClick={setAllDays}>매일</button>
              </div>
            </div>
            <div className="sf__days">
              {DAY_KEYS.map(d => (
                <button
                  key={d}
                  className={`sf__day-btn ${form.repeatDays.includes(d) ? 'sf__day-btn--on' : ''}`}
                  onClick={() => toggleDay(d)}
                >
                  {DAY_SHORT[d]}
                </button>
              ))}
            </div>
          </div>
        </div>

        {formError && (
          <p style={{ color: '#EF4444', fontSize: 12, fontWeight: 500, padding: '0 22px 8px', margin: 0 }}>{formError}</p>
        )}

        <div className="sheet__footer">
          <button className="sheet__btn sheet__btn--cancel" onClick={onClose} disabled={isSaving}>취소</button>
          <button className="sheet__btn sheet__btn--save" onClick={handleSave} disabled={isSaving}>
            {isSaving ? '저장 중...' : isEditing ? '수정 완료' : '저장'}
          </button>
        </div>
      </div>

      {placeField && (
        <PlaceSearchOverlay
          field={placeField}
          onSelect={handlePlaceSelect}
          onClose={() => setPlaceField(null)}
        />
      )}
    </>
  );
}

/* ================================================================
   DayTimeline — 오늘 일정 타임라인
   ================================================================ */

function DayTimeline({ schedules }) {
  const now = new Date();
  const nowMins = now.getHours() * 60 + now.getMinutes();

  const toMins = (timeStr) => {
    if (!timeStr) return 9999;
    const [h, m] = timeStr.split(':').map(Number);
    return h * 60 + m;
  };

  const sorted = [...schedules].sort((a, b) =>
    toMins(a.departureTime || a.arrivalTime) - toMins(b.departureTime || b.arrivalTime)
  );

  const nextIdx = sorted.findIndex(s => toMins(s.departureTime || s.arrivalTime) > nowMins);

  return (
    <div className="cal-tl">
      <div className="cal-tl__title">오늘 타임라인</div>
      {sorted.map((sch, i) => {
        const deptMins = toMins(sch.departureTime || sch.arrivalTime);
        const past     = deptMins < nowMins;
        const isNext   = i === nextIdx;
        return (
          <div
            key={sch.scheduleId}
            className={`cal-tl__item${past ? ' cal-tl__item--past' : ''}${isNext ? ' cal-tl__item--next' : ''}`}
          >
            <div className="cal-tl__times">
              <span className="cal-tl__dept">{sch.departureTime || '—'}</span>
              <span className="cal-tl__arr">{sch.arrivalTime} 도착</span>
            </div>
            <div className="cal-tl__line">
              <div className="cal-tl__dot" />
              {i < sorted.length - 1 && <div className="cal-tl__connector" />}
            </div>
            <div className="cal-tl__info">
              <div className="cal-tl__name">{sch.title}</div>
              <div className="cal-tl__route">
                {sch.originName} → {sch.destinationName}
              </div>
              {isNext && <span className="cal-tl__next-badge">다음 일정</span>}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ================================================================
   RoutePreviewSheet — 경로 미리보기 시트
   ================================================================ */

function RoutePreviewSheet({ schedule, onClose }) {
  const [routeState, setRouteState] = useState('loading'); // 'loading' | 'ready' | 'error'
  const [routeData, setRouteData]   = useState(null);

  useEffect(() => {
    if (!schedule?.scheduleId) { setRouteState('error'); return; }
    let cancelled = false;
    setRouteState('loading');
    api.route.get(schedule.scheduleId)
      .then(data => { if (!cancelled) { setRouteData(data); setRouteState('ready'); } })
      .catch(err => { if (!cancelled) { console.error('[RoutePreview]', err); setRouteState('error'); } });
    return () => { cancelled = true; };
  }, [schedule?.scheduleId]);

  const route = routeData?.route;
  const segments = route?.segments ?? [];

  // 도보 구간 총 시간
  const walkMins = segments
    .filter(s => s.mode === 'WALK')
    .reduce((sum, s) => sum + s.durationMinutes, 0);

  // 대중교통 구간 (첫 번째)
  const transitSeg = segments.find(s => s.mode !== 'WALK');

  return (
    <>
      <div className="sheet-backdrop" onClick={onClose} />
      <div className="cal-route-sheet">
        <div className="sheet__handle" />
        <div className="cal-route-sheet__header">
          <span className="cal-route-sheet__title">{schedule.title} — 경로</span>
          <button className="cal-route-sheet__close" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M18 6L6 18M6 6l12 12" stroke="#A09A93" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </button>
        </div>

        <div className="cal-route-sheet__body">
          {routeState === 'loading' && (
            <div className="place-overlay__status">
              <div className="place-overlay__spinner" />
              경로 불러오는 중...
            </div>
          )}
          {routeState === 'error' && (
            <div className="place-overlay__status">경로 정보를 불러올 수 없어요</div>
          )}
          {routeState === 'ready' && route && (
            <>
              {/* 경로 요약 */}
              <div className="cal-rp__row">
                <div className="cal-rp__cell">
                  <span className="cal-rp__label">총 소요</span>
                  <span className="cal-rp__val">{route.totalDurationMinutes}분</span>
                  <span className="cal-rp__sub">도보 {walkMins}분</span>
                </div>
                <div className="cal-rp__divider" />
                <div className="cal-rp__cell">
                  <span className="cal-rp__label">{transitSeg ? (transitSeg.mode === 'SUBWAY' ? '지하철' : '버스') : '경로'}</span>
                  <span className="cal-rp__val">{transitSeg?.lineName || '—'}</span>
                  <span className="cal-rp__sub">{transitSeg ? `${transitSeg.durationMinutes}분 소요` : ''}</span>
                </div>
              </div>

              {/* 소요 시간 */}
              <div className="cal-rp__timeline">
                {segments.map((seg, i) => (
                  <React.Fragment key={i}>
                    <div className="cal-rp__tl-item">
                      <span className="cal-rp__tl-icon">{seg.mode === 'WALK' ? '🚶' : seg.mode === 'SUBWAY' ? '🚇' : '🚌'}</span>
                      <span className="cal-rp__tl-val">{seg.durationMinutes}분</span>
                      <span className="cal-rp__tl-sub">{seg.mode === 'WALK' ? '도보' : seg.lineName || '대중교통'}</span>
                    </div>
                    {i < segments.length - 1 && <span className="cal-rp__tl-arrow">→</span>}
                  </React.Fragment>
                ))}
                <span className="cal-rp__tl-arrow">=</span>
                <div className="cal-rp__tl-item cal-rp__tl-item--total">
                  <span className="cal-rp__tl-val">총 {route.totalDurationMinutes}분</span>
                  <span className="cal-rp__tl-sub">소요</span>
                </div>
              </div>

              {/* 메타 */}
              <div className="cal-rp__meta">
                <span className="cal-rp__chip">환승 {Math.max(0, (route.transferCount ?? 0) - 1) === 0 ? '없음' : `${Math.max(0, (route.transferCount ?? 0) - 1)}회`}</span>
                <span className="cal-rp__chip">요금 {route.payment?.toLocaleString() ?? '—'}원</span>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

/* ================================================================
   ScheduleCard — 플랫 카드 (항상 펼쳐진 형태)
   ================================================================ */

const DAY_ORDER = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

function calcDepTime(arrivalTime, durationMin) {
  if (!arrivalTime || !durationMin) return null;
  const [h, m] = arrivalTime.split(':').map(Number);
  const dep = h * 60 + m - durationMin;
  const norm = ((dep % 1440) + 1440) % 1440;
  return `${String(Math.floor(norm / 60)).padStart(2, '0')}:${String(norm % 60).padStart(2, '0')}`;
}

function ScheduleAccordion({ schedule, onEdit, onDelete, todayDow }) {
  const [open, setOpen] = useState(false);

  // API 응답: arrivalTime이 ISO datetime일 수 있으므로 "HH:mm" 추출
  const rawArr = schedule.arrivalTime || '';
  const displayArr = rawArr.includes('T') ? rawArr.split('T')[1]?.substring(0, 5) : rawArr;

  const depTime = calcDepTime(displayArr, schedule.estimatedDurationMinutes ?? schedule.averageDurationMinutes);
  const days = schedule.repeatDays ?? schedule.routineRule?.daysOfWeek ?? [];
  const originName = schedule.originName ?? schedule.origin?.name ?? '';
  const destName   = schedule.destinationName ?? schedule.destination?.name ?? '';

  return (
    <div className="sac" onClick={() => setOpen(o => !o)}>

      {/* 1줄: 제목 + 도착시간 */}
      <div className="sac-row1">
        <span className="sac-title">{schedule.title}</span>
        <span className="sac-arr">{displayArr}</span>
      </div>

      {/* 2줄: 출발 → 도착 시간 */}
      <div className="sac-row2">
        {depTime ? (
          <span className="sac-times">
            <span className="sac-times__num">{depTime}</span>
            <span className="sac-times__label"> 출발 </span>
            <span className="sac-times__arrow">→</span>
            <span className="sac-times__label"> </span>
            <span className="sac-times__num">{displayArr}</span>
            <span className="sac-times__label"> 도착</span>
          </span>
        ) : (
          <span className="sac-times">
            <span className="sac-times__label">도착 </span>
            <span className="sac-times__num">{displayArr}</span>
          </span>
        )}
      </div>

      {/* 3줄: 요일 텍스트 */}
      <div className="sac-days">
        {DAY_KEYS.map(key => {
          const isOn    = days.includes(key);
          const isToday = DAY_NUM[key] === todayDow;
          return (
            <span
              key={key}
              className={[
                'sac-day',
                isOn && isToday ? 'sac-day--today' :
                isOn            ? 'sac-day--on'    : '',
              ].join(' ')}
            >
              {DAY_SHORT[key]}
            </span>
          );
        })}
      </div>

      {/* 펼쳐지는 영역 */}
      <div className={`sac-expand ${open ? 'sac-expand--open' : ''}`}>
        <div className="sac-expand__inner">
          <div className="sac-divider" />
          <p className="sac-expand__route">
            {originName} → {destName}
          </p>
          <p className="sac-depart__sub">
            {schedule.estimatedDurationMinutes ? `${schedule.estimatedDurationMinutes}분` : '경로 계산 중'}
          </p>
          <div className="sac-actions">
            <button className="sac-btn sac-btn--edit"
              onClick={e => { e.stopPropagation(); onEdit(schedule); }}>수정</button>
            <button className="sac-btn sac-btn--del"
              onClick={e => { e.stopPropagation(); onDelete(schedule.scheduleId); }}>삭제</button>
          </div>
        </div>
      </div>

    </div>
  );
}

/* ── 빈 날 카드 ── */
function EmptyDayCard() {
  return (
    <div className="sac sac--empty">
      <p className="sac-empty__text">이 날에는 일정이 없어요</p>
    </div>
  );
}


/* ================================================================
   CalendarPage — 메인
   ================================================================ */

function CalendarPage() {
  const today     = new Date();
  const todayY    = today.getFullYear();
  const todayM    = today.getMonth();
  const todayD    = today.getDate();

  const [year, setYear]         = useState(todayY);
  const [month, setMonth]       = useState(todayM);
  const [selDay, setSelDay]     = useState(todayD);
  const [schedules, setSchedules] = useState([]);
  const [showSheet, setShowSheet]   = useState(false);
  const [editingSch, setEditingSch] = useState(null);
  const [isSaving, setIsSaving]     = useState(false);
  const [toast, setToast]           = useState('');
  const todayDow = today.getDay();

  const [searchParams] = useSearchParams();
  const [uiState, setUiState] = useState('loading');

  const fetchSchedules = useCallback(async () => {
    setUiState('loading');
    try {
      const data = await api.schedules.list();
      setSchedules(data.items);
      setUiState('ready');
    } catch (err) {
      console.error('[Calendar] 일정 로드 실패', err);
      setUiState('error');
    }
  }, []);

  useEffect(() => {
    const forced = searchParams.get('state');
    if (forced === 'loading') return;
    if (forced === 'error') { setUiState('error'); return; }
    fetchSchedules();
  }, [searchParams, fetchSchedules]);

  const retry = () => fetchSchedules();

  // 이번 주 일정 수
  const thisWeekCount = useMemo(() => {
    const weekStart = new Date(today);
    weekStart.setDate(today.getDate() - today.getDay());
    let count = 0;
    for (let i = 0; i < 7; i++) {
      const d = new Date(weekStart);
      d.setDate(weekStart.getDate() + i);
      count += getSchedulesForDate(schedules, d.getFullYear(), d.getMonth(), d.getDate()).length;
    }
    return count;
  }, [schedules]); // eslint-disable-line react-hooks/exhaustive-deps

  const isToday = selDay === todayD && month === todayM && year === todayY;

  const prevMonth = () => {
    if (month === 0) { setYear(y => y - 1); setMonth(11); }
    else setMonth(m => m - 1);
    setSelDay(1);
  };
  const nextMonth = () => {
    if (month === 11) { setYear(y => y + 1); setMonth(0); }
    else setMonth(m => m + 1);
    setSelDay(1);
  };

  const calDays = getCalendarDays(year, month);
  const daySchedules = getSchedulesForDate(schedules, year, month, selDay);
  const selDow = new Date(year, month, selDay).getDay();

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 2000); };

  const openAdd = () => { setEditingSch(null); setShowSheet(true); };
  const openEdit = (sch) => { setEditingSch(sch); setShowSheet(true); };
  const closeSheet = () => { setShowSheet(false); setEditingSch(null); };

  const handleSave = async (form) => {
    // 백엔드 Place 스키마 provider enum: NAVER | KAKAO | ODSAY | MANUAL
    // geocode API는 'KAKAO_LOCAL' 반환하므로 schedule 저장 시 'KAKAO'로 변환
    const normalizeProvider = (p) => (p === 'KAKAO_LOCAL' ? 'KAKAO' : (p ?? 'KAKAO'));

    const body = {
      title: form.title,
      origin: {
        name:     form.originPlace?.name     ?? form.originName,
        lat:      form.originPlace?.lat      ?? null,
        lng:      form.originPlace?.lng      ?? null,
        address:  form.originPlace?.address  ?? null,
        placeId:  form.originPlace?.placeId  ?? null,
        provider: normalizeProvider(form.originPlace?.provider),
      },
      destination: {
        name:     form.destinationPlace?.name     ?? form.destinationName,
        lat:      form.destinationPlace?.lat      ?? null,
        lng:      form.destinationPlace?.lng      ?? null,
        address:  form.destinationPlace?.address  ?? null,
        placeId:  form.destinationPlace?.placeId  ?? null,
        provider: normalizeProvider(form.destinationPlace?.provider),
      },
      userDepartureTime: `${year}-${padTwo(month + 1)}-${padTwo(selDay)}T${form.usualDepartureTime || '08:00'}:00+09:00`,
      arrivalTime: `${year}-${padTwo(month + 1)}-${padTwo(selDay)}T${form.arrivalTime}:00+09:00`,
      reminderOffsetMinutes: 5,
      routineRule: form.repeatDays.length > 0
        ? { type: 'WEEKLY', daysOfWeek: form.repeatDays }
        : null,
    };

    setIsSaving(true);
    try {
      if (editingSch) {
        const updated = await api.schedules.update(editingSch.scheduleId, body);
        setSchedules(s => s.map(x => x.scheduleId === updated.scheduleId ? updated : x));
        showToast('일정이 수정되었어요');
      } else {
        const created = await api.schedules.create(body);
        setSchedules(s => [...s, created]);
        showToast(created.routeStatus === 'PENDING_RETRY'
          ? '일정은 등록됐어요. 경로 계산이 지연되고 있어요'
          : '일정이 등록되었어요');
      }
      closeSheet();
    } catch (err) {
      console.error('[Calendar] 일정 저장 실패', err);
      showToast(err.message || '일정 저장에 실패했어요');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = useCallback(async (id) => {
    try {
      await api.schedules.delete(id);
      setSchedules(s => s.filter(x => x.scheduleId !== id));
      showToast('일정이 삭제되었어요');
    } catch (err) {
      console.error('[Calendar] 일정 삭제 실패', err);
      showToast(err.message || '일정 삭제에 실패했어요');
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const monthLabel = `${year}년 ${month + 1}월`;
  const selLabel   = `${month + 1}월 ${selDay}일 (${DAY_LABELS[selDow]})`;

  return (
    <div className="cal-page">
      <div className="cal-container">
        {uiState === 'loading' && <CalendarSkeletons />}
        {uiState === 'error'   && <ErrorState onRetry={retry} />}
        {uiState === 'ready'   && (<>

        {/* ── 월 네비게이션 ── */}
        <div className="cal-nav">
          <button className="cal-nav__arrow" onClick={prevMonth}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M15 18l-6-6 6-6" stroke="#1C1C1E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
          <span className="cal-nav__label">{monthLabel}</span>
          <button className="cal-nav__arrow" onClick={nextMonth}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M9 18l6-6-6-6" stroke="#1C1C1E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        </div>

        {/* ── 이번 주 요약 배지 ── */}
        {thisWeekCount > 0 && (
          <div className="cal-week-summary">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
              <path d="M3 10h18M8 2v4M16 2v4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
            이번 주 일정 {thisWeekCount}개
          </div>
        )}

        {/* ── 달력 카드 ── */}
        <div className="cal-card">
          {/* 요일 헤더 */}
          <div className="cal-weekdays">
            {DAY_LABELS.map((d, i) => (
              <span key={d} className={`cal-wd ${i === 0 ? 'cal-wd--sun' : i === 6 ? 'cal-wd--sat' : ''}`}>
                {d}
              </span>
            ))}
          </div>

          {/* 날짜 그리드 */}
          <div className="cal-grid">
            {calDays.map((day, i) => {
              if (day === null) return <div key={`e-${i}`} className="cal-cell cal-cell--empty" />;
              const col     = i % 7;
              const isToday = day === todayD && month === todayM && year === todayY;
              const isSel   = day === selDay && !isToday;
              const dotCount = getSchedulesForDate(schedules, year, month, day).length;
              const isSun   = col === 0;
              const isSat   = col === 6;
              const dotClass = dotCount === 0 ? ''
                : dotCount === 1 ? 'cal-cell__dot--1'
                : dotCount === 2 ? 'cal-cell__dot--2'
                : 'cal-cell__dot--3';
              return (
                <div
                  key={`d-${day}`}
                  className={[
                    'cal-cell',
                    isToday ? 'cal-cell--today'    : '',
                    isSel   ? 'cal-cell--selected' : '',
                    isSun   ? 'cal-cell--sun'      : '',
                    isSat   ? 'cal-cell--sat'      : '',
                  ].join(' ')}
                  onClick={() => setSelDay(day)}
                >
                  <span className="cal-cell__num">{day}</span>
                  {dotCount > 0 && <span className={`cal-cell__dot ${dotClass}`} />}
                </div>
              );
            })}
          </div>
        </div>

        {/* ── 선택된 날짜 일정 ── */}
        <div className="cal-section">
          <div className="cal-section__header">
            <h3 className="cal-section__title">{selLabel}</h3>
            <button className="cal-add-btn" onClick={openAdd}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <path d="M12 5v14M5 12h14" stroke="white" strokeWidth="2.5" strokeLinecap="round"/>
              </svg>
              새 일정
            </button>
          </div>

          {/* 아코디언 카드 스택 */}
          <div className="cal-stack">
            {daySchedules.length === 0 && <EmptyDayCard />}
            {daySchedules.map(sch => (
              <ScheduleAccordion
                key={sch.scheduleId}
                schedule={sch}
                onEdit={openEdit}
                onDelete={handleDelete}
                todayDow={todayDow}
              />
            ))}
          </div>

        </div>

        </>)}
      </div>

      {/* ── 일정 추가/수정 바텀시트 ── */}
      {showSheet && (
        <BottomSheet
          editingSchedule={editingSch}
          onClose={closeSheet}
          onSave={handleSave}
          isSaving={isSaving}
        />
      )}

      {toast && <div className="st-toast">{toast}</div>}
    </div>
  );
}

export default CalendarPage;
