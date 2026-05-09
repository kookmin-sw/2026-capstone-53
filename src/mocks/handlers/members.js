import { http, HttpResponse } from 'msw';
import { seedMember, activeTokens } from '../data/members';
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
  if (!auth || !activeTokens.access || auth !== `Bearer ${activeTokens.access}`) {
    return HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: '인증 필요', details: null } },
      { status: 401 },
    );
  }
  return null;
}

export const membersHandlers = [
  // GET /members/me
  http.get(`${API}/members/me`, ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    return HttpResponse.json({
      data: {
        memberId:  seedMember.memberId,
        loginId:   seedMember.loginId,
        nickname:  seedMember.nickname,
        createdAt: seedMember.createdAt,
      },
    });
  }),

  // PATCH /members/me
  http.patch(`${API}/members/me`, async ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    const body = await request.json();
    if (body.nickname) seedMember.nickname = body.nickname;

    return HttpResponse.json({
      data: {
        memberId:  seedMember.memberId,
        loginId:   seedMember.loginId,
        nickname:  seedMember.nickname,
        createdAt: seedMember.createdAt,
      },
    });
  }),

  // DELETE /members/me
  http.delete(`${API}/members/me`, ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;
    return new HttpResponse(null, { status: 204 });
  }),
];
