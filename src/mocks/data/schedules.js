/** @typedef {import('../../types/api')} T */

/** @type {T.Schedule[]} */
export const seedSchedules = [
  // 1. 단발성 일정 — 경로 계산 완료
  {
    scheduleId: 'sch_01HSEED0001ABCDEFGHJKLMN',
    title: '국민대 등교',
    origin:      { name: '우이동',     lat: 37.6600, lng: 127.0120 },
    destination: { name: '국민대학교', lat: 37.6103, lng: 126.9969 },
    userDepartureTime:        '2026-04-21T08:30:00+09:00',
    arrivalTime:              '2026-04-21T09:00:00+09:00',
    estimatedDurationMinutes: 35,
    recommendedDepartureTime: '2026-04-21T08:25:00+09:00',
    departureAdvice:          'LATER',
    reminderOffsetMinutes:    5,
    reminderAt:               '2026-04-21T08:20:00+09:00',
    routineRule:              null,
    routeStatus:              'CALCULATED',
    routeCalculatedAt:        '2026-04-20T15:00:00+09:00',
    createdAt:                '2026-04-20T15:00:00+09:00',
  },

  // 2. 루틴 일정 — 매주 월/수/금
  {
    scheduleId: 'sch_01HSEED0002ABCDEFGHJKLMN',
    title: '토익 학원',
    origin:      { name: '우이동',         lat: 37.6600, lng: 127.0120 },
    destination: { name: '강남역 토익학원', lat: 37.4979, lng: 127.0276 },
    userDepartureTime:        '2026-04-21T13:00:00+09:00',
    arrivalTime:              '2026-04-21T14:00:00+09:00',
    estimatedDurationMinutes: 55,
    recommendedDepartureTime: '2026-04-21T12:55:00+09:00',
    departureAdvice:          'ON_TIME',
    reminderOffsetMinutes:    10,
    reminderAt:               '2026-04-21T12:45:00+09:00',
    routineRule:              { type: 'WEEKLY', daysOfWeek: ['MON', 'WED', 'FRI'] },
    routeStatus:              'CALCULATED',
    routeCalculatedAt:        '2026-04-20T15:10:00+09:00',
    createdAt:                '2026-04-20T15:10:00+09:00',
  },

  // 3. 경로 계산 실패 일정
  {
    scheduleId: 'sch_01HSEED0003ABCDEFGHJKLMN',
    title: '동아리 모임',
    origin:      { name: '국민대학교',   lat: 37.6103, lng: 126.9969 },
    destination: { name: '홍대입구역 카페', lat: 37.5573, lng: 126.9245 },
    userDepartureTime:        '2026-04-21T18:00:00+09:00',
    arrivalTime:              '2026-04-21T19:00:00+09:00',
    estimatedDurationMinutes: null,
    recommendedDepartureTime: null,
    departureAdvice:          null,
    reminderOffsetMinutes:    5,
    reminderAt:               null,
    routineRule:              null,
    routeStatus:              'PENDING_RETRY',
    routeCalculatedAt:        null,
    createdAt:                '2026-04-20T16:00:00+09:00',
  },
];

// 다음 scheduleId 카운터
let nextId = 4;
export function generateScheduleId() {
  return `sch_01HSEED${String(nextId++).padStart(4, '0')}ABCDEFGHJK`;
}
