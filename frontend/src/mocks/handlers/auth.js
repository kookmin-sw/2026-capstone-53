import { http, HttpResponse } from 'msw';
import { seedMember, SEED_ACCESS_TOKEN, SEED_REFRESH_TOKEN } from '../data/members';

const API = 'http://localhost:8080/api/v1';

export const authHandlers = [
  // POST /auth/signup
  http.post(`${API}/auth/signup`, async ({ request }) => {
    const body = await request.json();
    if (!body.loginId || !body.password || !body.nickname) {
      return HttpResponse.json(
        { error: { code: 'VALIDATION_ERROR', message: '필수 필드 누락', details: null } },
        { status: 400 },
      );
    }
    if (body.loginId === seedMember.loginId) {
      return HttpResponse.json(
        { error: { code: 'LOGIN_ID_DUPLICATED', message: '이미 사용 중인 아이디', details: null } },
        { status: 409 },
      );
    }
    return HttpResponse.json({
      data: {
        memberId: `mem_01HNEW${Date.now()}`,
        loginId:  body.loginId,
        nickname: body.nickname,
        accessToken:  SEED_ACCESS_TOKEN,
        refreshToken: SEED_REFRESH_TOKEN,
      },
    }, { status: 201 });
  }),

  // POST /auth/login
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = await request.json();
    if (body.loginId !== seedMember.loginId || body.password !== seedMember.password) {
      return HttpResponse.json(
        { error: { code: 'INVALID_CREDENTIALS', message: '아이디 또는 비밀번호 불일치', details: null } },
        { status: 401 },
      );
    }
    return HttpResponse.json({
      data: {
        memberId:     seedMember.memberId,
        accessToken:  SEED_ACCESS_TOKEN,
        refreshToken: SEED_REFRESH_TOKEN,
      },
    });
  }),

  // POST /auth/logout
  http.post(`${API}/auth/logout`, () => {
    return new HttpResponse(null, { status: 204 });
  }),
];
