import { http, HttpResponse } from 'msw';
import { seedSchedules, generateScheduleId } from '../data/schedules';
import { SEED_ACCESS_TOKEN } from '../data/members';
import { isScenario } from '../scenarios';

const API = 'http://localhost:8080/api/v1';

function requireAuth(request) {
  if (isScenario('token-expired')) {
    return HttpResponse.json(
      { error: { code: 'TOKEN_EXPIRED', message: '토큰 만료', details: null } },
      { status: 401 },
    );
  }
  const auth = request.headers.get('Authorization');
  if (auth !== `Bearer ${SEED_ACCESS_TOKEN}`) {
    return HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: '인증 필요', details: null } },
      { status: 401 },
    );
  }
  return null;
}

export const schedulesHandlers = [
  // POST /schedules
  http.post(`${API}/schedules`, async ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const body = await request.json();
    const now = new Date().toISOString().replace('Z', '+09:00');

    const newSch = {
      scheduleId:               generateScheduleId(),
      title:                    body.title,
      origin:                   body.origin,
      destination:              body.destination,
      userDepartureTime:        body.userDepartureTime,
      arrivalTime:              body.arrivalTime,
      estimatedDurationMinutes: isScenario('route-pending-retry') ? null : 35,
      recommendedDepartureTime: isScenario('route-pending-retry') ? null : body.userDepartureTime,
      departureAdvice:          isScenario('route-pending-retry') ? null : 'LATER',
      reminderOffsetMinutes:    body.reminderOffsetMinutes ?? 5,
      reminderAt:               isScenario('route-pending-retry') ? null : now,
      routineRule:              body.routineRule ?? null,
      routeStatus:              isScenario('route-pending-retry') ? 'PENDING_RETRY' : 'CALCULATED',
      routeCalculatedAt:        isScenario('route-pending-retry') ? null : now,
      createdAt:                now,
    };

    seedSchedules.push(newSch);
    return HttpResponse.json({ data: newSch }, { status: 201 });
  }),

  // GET /schedules
  http.get(`${API}/schedules`, ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const url = new URL(request.url);
    const limit = parseInt(url.searchParams.get('limit') || '20', 10);

    return HttpResponse.json({
      data: {
        items:      seedSchedules.slice(0, limit),
        nextCursor: null,
        hasMore:    false,
      },
    });
  }),

  // GET /schedules/:id
  http.get(`${API}/schedules/:scheduleId`, ({ request, params }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const sch = seedSchedules.find(s => s.scheduleId === params.scheduleId);
    if (!sch) {
      return HttpResponse.json(
        { error: { code: 'SCHEDULE_NOT_FOUND', message: '일정 없음', details: null } },
        { status: 404 },
      );
    }
    return HttpResponse.json({ data: sch });
  }),

  // PATCH /schedules/:id
  http.patch(`${API}/schedules/:scheduleId`, async ({ request, params }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const idx = seedSchedules.findIndex(s => s.scheduleId === params.scheduleId);
    if (idx === -1) {
      return HttpResponse.json(
        { error: { code: 'SCHEDULE_NOT_FOUND', message: '일정 없음', details: null } },
        { status: 404 },
      );
    }
    const body = await request.json();
    seedSchedules[idx] = { ...seedSchedules[idx], ...body };
    return HttpResponse.json({ data: seedSchedules[idx] });
  }),

  // DELETE /schedules/:id
  http.delete(`${API}/schedules/:scheduleId`, ({ request, params }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const idx = seedSchedules.findIndex(s => s.scheduleId === params.scheduleId);
    if (idx === -1) {
      return HttpResponse.json(
        { error: { code: 'SCHEDULE_NOT_FOUND', message: '일정 없음', details: null } },
        { status: 404 },
      );
    }
    seedSchedules.splice(idx, 1);
    return new HttpResponse(null, { status: 204 });
  }),
];
