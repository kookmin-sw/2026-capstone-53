// ================================================================
// API Client — TodayWay v1.1.11-MVP
// REACT_APP_API_URL 이 없으면 mock 데이터 반환
// ================================================================

import {
  mockLoginResponse,
  mockSignupResponse,
  mockMember,
  mockMainData,
  mockScheduleList,
  mockScheduleDetail,
  mockScheduleCreateResponse,
  mockRouteData,
  mockMapConfig,
  mockGeocodeResponse,
} from '../data/mockData';

const BASE_URL = process.env.REACT_APP_API_URL
  ? `${process.env.REACT_APP_API_URL}/api/v1`
  : null;

const IS_MOCK = !BASE_URL;

// ── 토큰 관리 ──────────────────────────────────────────────────

export const tokenStorage = {
  getAccess:    () => localStorage.getItem('accessToken'),
  getRefresh:   () => localStorage.getItem('refreshToken'),
  setTokens:    (access, refresh) => {
    localStorage.setItem('accessToken', access);
    if (refresh) localStorage.setItem('refreshToken', refresh);
  },
  clear:        () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  },
};

// ── 공통 fetch 함수 ────────────────────────────────────────────

async function apiFetch(path, options = {}) {
  const token = tokenStorage.getAccess();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    tokenStorage.clear();
    window.location.href = '/login';
    return;
  }

  const json = await res.json();

  if (!res.ok) {
    const err = json?.error ?? {};
    throw Object.assign(new Error(err.message ?? 'API 오류'), {
      code: err.code,
      details: err.details,
      status: res.status,
    });
  }

  return json; // { data: T }
}

// ── mock 지연 헬퍼 (실제감) ──────────────────────────────────

const delay = (ms = 300) => new Promise(r => setTimeout(r, ms));

// ================================================================
// 1. 인증 (Auth)
// ================================================================

/**
 * POST /auth/signup
 * @param {{ loginId, password, nickname }} body
 * @returns {{ memberId, loginId, nickname, accessToken, refreshToken }}
 */
export async function signup(body) {
  if (IS_MOCK) {
    await delay();
    const { data } = mockSignupResponse;
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    return mockSignupResponse;
  }
  const res = await apiFetch('/auth/signup', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  tokenStorage.setTokens(res.data.accessToken, res.data.refreshToken);
  return res;
}

/**
 * POST /auth/login
 * @param {{ loginId, password }} body
 * @returns {{ memberId, accessToken, refreshToken }}
 */
export async function login(body) {
  if (IS_MOCK) {
    await delay();
    const { data } = mockLoginResponse;
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    return mockLoginResponse;
  }
  const res = await apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  tokenStorage.setTokens(res.data.accessToken, res.data.refreshToken);
  return res;
}

/**
 * POST /auth/logout
 */
export async function logout() {
  if (IS_MOCK) {
    await delay(100);
    tokenStorage.clear();
    return;
  }
  await apiFetch('/auth/logout', { method: 'POST' });
  tokenStorage.clear();
}

// ================================================================
// 2. 회원 정보 (Member)
// ================================================================

/**
 * GET /members/me
 * @returns {{ memberId, loginId, nickname, preferences, createdAt }}
 */
export async function getMe() {
  if (IS_MOCK) { await delay(); return mockMember; }
  return apiFetch('/members/me');
}

/**
 * PATCH /members/me
 * @param {{ nickname?, preferences? }} body
 */
export async function updateMe(body) {
  if (IS_MOCK) {
    await delay();
    return { data: { ...mockMember.data, ...body } };
  }
  return apiFetch('/members/me', {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

/**
 * DELETE /members/me
 */
export async function deleteMe() {
  if (IS_MOCK) {
    await delay();
    tokenStorage.clear();
    return;
  }
  await apiFetch('/members/me', { method: 'DELETE' });
  tokenStorage.clear();
}

// ================================================================
// 3. 메인 화면 (Main)
// ================================================================

/**
 * GET /main
 * @returns {{ nearestSchedule, mapCenter }}
 */
export async function getMain() {
  if (IS_MOCK) { await delay(); return mockMainData; }
  return apiFetch('/main');
}

/**
 * GET /map/config
 * @returns {{ provider, defaultZoom, defaultCenter, tileStyle }}
 */
export async function getMapConfig() {
  if (IS_MOCK) { await delay(); return mockMapConfig; }
  return apiFetch('/map/config');
}

// ================================================================
// 4. 일정 (Schedules)
// ================================================================

/**
 * POST /schedules
 * @param {{ title, origin, destination, arrivalTime, reminderOffsetMinutes, routineRule? }} body
 * @returns {{ scheduleId, averageDurationMinutes, reminderAt, nextOccurrence }}
 */
export async function createSchedule(body) {
  if (IS_MOCK) { await delay(); return mockScheduleCreateResponse; }
  return apiFetch('/schedules', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/**
 * GET /schedules
 * @param {{ cursor?, limit?, from?, to? }} params
 * @returns {{ data: Schedule[], meta }}
 */
export async function getSchedules(params = {}) {
  if (IS_MOCK) { await delay(); return mockScheduleList; }
  const qs = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v != null)
  ).toString();
  return apiFetch(`/schedules${qs ? `?${qs}` : ''}`);
}

/**
 * GET /schedules/:id
 * @param {string} scheduleId
 */
export async function getSchedule(scheduleId) {
  if (IS_MOCK) { await delay(); return mockScheduleDetail; }
  return apiFetch(`/schedules/${scheduleId}`);
}

/**
 * PATCH /schedules/:id
 * @param {string} scheduleId
 * @param {Partial<ScheduleBody>} body
 */
export async function updateSchedule(scheduleId, body) {
  if (IS_MOCK) {
    await delay();
    return { data: { ...mockScheduleDetail.data, ...body } };
  }
  return apiFetch(`/schedules/${scheduleId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

/**
 * DELETE /schedules/:id
 * @param {string} scheduleId
 */
export async function deleteSchedule(scheduleId) {
  if (IS_MOCK) { await delay(200); return; }
  return apiFetch(`/schedules/${scheduleId}`, { method: 'DELETE' });
}

// ================================================================
// 5. 경로 (Route)
// ================================================================

/**
 * GET /schedules/:id/route
 * @param {string} scheduleId
 * @param {boolean} forceRefresh
 * @returns {{ scheduleId, candidates, calculatedAt }}
 */
export async function getRoute(scheduleId, forceRefresh = false) {
  if (IS_MOCK) { await delay(600); return mockRouteData; }
  return apiFetch(
    `/schedules/${scheduleId}/route?forceRefresh=${forceRefresh}`
  );
}

// ================================================================
// 6. 푸시 알림 (Push)
// ================================================================

/**
 * POST /push/subscribe
 * @param {{ endpoint, keys: { p256dh, auth } }} subscription  Web Push 구독 객체
 */
export async function subscribePush(subscription) {
  if (IS_MOCK) { await delay(); return; }
  return apiFetch('/push/subscribe', {
    method: 'POST',
    body: JSON.stringify(subscription),
  });
}

/**
 * DELETE /push/subscribe
 * @param {{ endpoint }} body
 */
export async function unsubscribePush(body) {
  if (IS_MOCK) { await delay(); return; }
  return apiFetch('/push/subscribe', {
    method: 'DELETE',
    body: JSON.stringify(body),
  });
}

// ================================================================
// 7. 지오코딩 (Geocode)
// ================================================================

/**
 * POST /geocode
 * @param {{ query: string }} body
 * @returns {{ matched, name, address, lat, lng, placeId, provider }}
 */
export async function geocode(query) {
  if (IS_MOCK) { await delay(400); return mockGeocodeResponse; }
  return apiFetch('/geocode', {
    method: 'POST',
    body: JSON.stringify({ query }),
  });
}
