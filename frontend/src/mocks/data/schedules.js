/** @typedef {import('../../types/api')} T */

// 시드 시간을 현재 기준 동적 생성 (모듈 로드 시 1회 계산, 새로고침 시 갱신)
const _now = Date.now();
const _iso = (offsetMin) => new Date(_now + offsetMin * 60000).toISOString();
const _today = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][new Date().getDay()];
const _weekdays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
const _addToday = (days) => (days.includes(_today) ? days : [...days, _today]);

/** @type {T.Schedule[]} */
export const seedSchedules = [
  // 1. 평일 루틴 — 학교 등교 (now+25분 출발, +60분 도착)
  {
    scheduleId: 'sch_01HSEED0001ABCDEFGHJKLMN',
    title: '국민대 등교',
    origin:      { name: '우이동',     lat: 37.6600, lng: 127.0120 },
    destination: { name: '국민대학교', lat: 37.6103, lng: 126.9969 },
    userDepartureTime:        _iso(30),
    arrivalTime:              _iso(60),
    estimatedDurationMinutes: 35,
    recommendedDepartureTime: _iso(25),
    departureAdvice:          'LATER',
    reminderOffsetMinutes:    5,
    reminderAt:               _iso(20),
    routineRule:              { type: 'WEEKLY', daysOfWeek: _addToday(_weekdays) },
    routeStatus:              'CALCULATED',
    routeCalculatedAt:        _iso(-10),
    createdAt:                _iso(-60),
  },

  // 2. 루틴 일정 — 매주 월/수/금 (now+4.5시간 출발, +5시간 도착)
  {
    scheduleId: 'sch_01HSEED0002ABCDEFGHJKLMN',
    title: '토익 학원',
    origin:      { name: '우이동',         lat: 37.6600, lng: 127.0120 },
    destination: { name: '강남역 토익학원', lat: 37.4979, lng: 127.0276 },
    userDepartureTime:        _iso(275),
    arrivalTime:              _iso(300),
    estimatedDurationMinutes: 55,
    recommendedDepartureTime: _iso(270),
    departureAdvice:          'ON_TIME',
    reminderOffsetMinutes:    10,
    reminderAt:               _iso(260),
    routineRule:              { type: 'WEEKLY', daysOfWeek: _addToday(['MON', 'WED', 'FRI']) },
    routeStatus:              'CALCULATED',
    routeCalculatedAt:        _iso(-10),
    createdAt:                _iso(-120),
  },

  // 3. 경로 계산 실패 일정 (now+9.5시간 출발, +10시간 도착)
  {
    scheduleId: 'sch_01HSEED0003ABCDEFGHJKLMN',
    title: '동아리 모임',
    origin:      { name: '국민대학교',   lat: 37.6103, lng: 126.9969 },
    destination: { name: '홍대입구역 카페', lat: 37.5573, lng: 126.9245 },
    userDepartureTime:        _iso(575),
    arrivalTime:              _iso(600),
    estimatedDurationMinutes: null,
    recommendedDepartureTime: null,
    departureAdvice:          null,
    reminderOffsetMinutes:    5,
    reminderAt:               null,
    routineRule:              { type: 'WEEKLY', daysOfWeek: _addToday(['FRI']) },
    routeStatus:              'PENDING_RETRY',
    routeCalculatedAt:        null,
    createdAt:                _iso(-180),
  },
];

// 다음 scheduleId 카운터
let nextId = 4;
export function generateScheduleId() {
  return `sch_01HSEED${String(nextId++).padStart(4, '0')}ABCDEFGHJK`;
}
