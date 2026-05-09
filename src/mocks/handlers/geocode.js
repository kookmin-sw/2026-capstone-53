import { http, HttpResponse } from 'msw';
import { activeTokens } from '../data/members';
import { isScenario } from '../scenarios';

const API = 'http://localhost:8080/api/v1';

const PLACES = [
  { name: '국민대학교',         address: '서울 성북구 정릉로 77',       lat: 37.6103, lng: 126.9969, placeId: '1001', provider: 'KAKAO' },
  { name: '강남역',             address: '서울 강남구 강남대로 396',     lat: 37.4979, lng: 127.0276, placeId: '1002', provider: 'KAKAO' },
  { name: '홍대입구역',         address: '서울 마포구 양화로 160',       lat: 37.5573, lng: 126.9245, placeId: '1003', provider: 'KAKAO' },
  { name: '우이동',             address: '서울 강북구 우이동',           lat: 37.6600, lng: 127.0120, placeId: '1004', provider: 'KAKAO' },
  { name: '쌍문동 피트니스센터', address: '서울 도봉구 쌍문로 112',       lat: 37.6485, lng: 127.0340, placeId: '1005', provider: 'KAKAO' },
];

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

export const geocodeHandlers = [
  // POST /geocode
  http.post(`${API}/geocode`, async ({ request }) => {
    const denied = requireAuth(request);
    if (denied) return denied;

    if (isScenario('external-timeout')) {
      return HttpResponse.json(
        { error: { code: 'EXTERNAL_TIMEOUT', message: '외부 API 타임아웃', details: null } },
        { status: 504 },
      );
    }

    const body = await request.json();
    const q = (body.query || '').toLowerCase();

    const match = PLACES.find(p =>
      p.name.toLowerCase().includes(q) || p.address.toLowerCase().includes(q)
    );

    if (!match) {
      return HttpResponse.json(
        { error: { code: 'GEOCODE_NO_MATCH', message: '검색 결과 없음', details: null } },
        { status: 404 },
      );
    }

    return HttpResponse.json({
      data: { matched: true, ...match },
    });
  }),
];
