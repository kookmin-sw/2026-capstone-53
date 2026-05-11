import { http, HttpResponse } from 'msw';
import { SEED_ACCESS_TOKEN } from '../data/members';
import { isScenario } from '../scenarios';

const API = 'http://localhost:8080/api/v1';

const subscriptions = new Map();

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

export const pushHandlers = [
  // POST /push/subscribe
  http.post(`${API}/push/subscribe`, async ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const body = await request.json();
    const subscriptionId = `sub_01HSEED${Date.now()}`;
    subscriptions.set(subscriptionId, body);

    return HttpResponse.json({ data: { subscriptionId } }, { status: 201 });
  }),

  // DELETE /push/subscribe/:id
  http.delete(`${API}/push/subscribe/:subscriptionId`, ({ request, params }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    if (!subscriptions.has(params.subscriptionId)) {
      return HttpResponse.json(
        { error: { code: 'SUBSCRIPTION_NOT_FOUND', message: '구독 없음', details: null } },
        { status: 404 },
      );
    }
    subscriptions.delete(params.subscriptionId);
    return new HttpResponse(null, { status: 204 });
  }),
];
