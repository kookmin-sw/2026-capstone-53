import { http, HttpResponse } from 'msw';
import { seedRoutes } from '../data/routes';
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

export const routeHandlers = [
  // GET /schedules/:id/route
  http.get(`${API}/schedules/:scheduleId/route`, ({ request, params }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    if (isScenario('external-route-failed')) {
      return HttpResponse.json(
        { error: { code: 'EXTERNAL_ROUTE_API_FAILED', message: 'ODsay 호출 실패', details: null } },
        { status: 502 },
      );
    }

    if (isScenario('external-timeout')) {
      return HttpResponse.json(
        { error: { code: 'EXTERNAL_TIMEOUT', message: '외부 API 타임아웃', details: null } },
        { status: 504 },
      );
    }

    const routeData = seedRoutes[params.scheduleId];
    if (!routeData) {
      return HttpResponse.json(
        { error: { code: 'ROUTE_NOT_CALCULATED', message: '경로 미계산', details: null } },
        { status: 404 },
      );
    }

    return HttpResponse.json({
      data: {
        scheduleId:   params.scheduleId,
        route:        routeData.route,
        calculatedAt: routeData.calculatedAt,
      },
    });
  }),
];
