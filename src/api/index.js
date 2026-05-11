/**
 * 오늘어디 — API 클라이언트
 * @module api
 *
 * 사용 예시:
 *   import { api } from '../api';
 *
 *   // 로그인
 *   const { accessToken } = await api.auth.login({ loginId: 'test', password: '12345678' });
 *
 *   // 일정 목록 조회
 *   const { items, hasMore } = await api.schedules.list({ limit: 10 });
 *
 *   // 에러 처리
 *   try {
 *     await api.schedules.create(body);
 *   } catch (err) {
 *     if (err.code === 'VALIDATION_ERROR') { ... }
 *     alert(err.message); // 한국어 메시지
 *   }
 */

import { apiFetch, tokenStorage } from './fetchClient.js';

/** @typedef {import('../types/api')} Types */

// ================================================================
//  §3 인증 (Auth)
// ================================================================

const auth = {
  /**
   * POST /auth/signup
   * @param {Types.SignupRequest} body
   * @returns {Promise<Types.SignupResponse>}
   */
  async signup(body) {
    const data = await apiFetch('/auth/signup', {
      method: 'POST',
      body: JSON.stringify(body),
      skipAuth: true,
    });
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    return data;
  },

  /**
   * POST /auth/login
   * @param {Types.LoginRequest} body
   * @returns {Promise<Types.LoginResponse>}
   */
  async login(body) {
    const data = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify(body),
      skipAuth: true,
    });
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    return data;
  },

  /**
   * POST /auth/logout
   * @returns {Promise<void>}
   */
  async logout() {
    const refreshToken = tokenStorage.getRefresh();
    await apiFetch('/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
      skipAuth: true,
    });
    tokenStorage.clear();
  },
};

// ================================================================
//  §4 회원 (Member)
// ================================================================

const members = {
  /**
   * GET /members/me
   * @returns {Promise<Types.GetMyInfoResponse>}
   */
  async me() {
    return apiFetch('/members/me');
  },

  /**
   * PATCH /members/me
   * @param {Types.UpdateMyInfoRequest} body
   * @returns {Promise<Types.UpdateMyInfoResponse>}
   */
  async update(body) {
    return apiFetch('/members/me', {
      method: 'PATCH',
      body: JSON.stringify(body),
    });
  },

  /**
   * DELETE /members/me
   * @returns {Promise<void>}
   */
  async delete() {
    await apiFetch('/members/me', { method: 'DELETE' });
    tokenStorage.clear();
  },
};

// ================================================================
//  §5 메인·지도 (Display)
// ================================================================

const main = {
  /**
   * GET /main
   * @param {Types.GetMainQuery} [query]
   * @returns {Promise<Types.GetMainResponse>}
   */
  async get(query = {}) {
    const qs = toQueryString(query);
    return apiFetch(`/main${qs}`);
  },
};

const map = {
  /**
   * GET /map/config
   * @returns {Promise<Types.GetMapConfigResponse>}
   */
  async config() {
    return apiFetch('/map/config');
  },
};

// ================================================================
//  §6 일정 (Schedule)
// ================================================================

const schedules = {
  /**
   * POST /schedules
   * @param {Types.CreateScheduleRequest} body
   * @returns {Promise<Types.CreateScheduleResponse>}
   */
  async create(body) {
    return apiFetch('/schedules', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  /**
   * GET /schedules
   * @param {Types.GetSchedulesQuery} [query]
   * @returns {Promise<Types.GetSchedulesResponse>}
   */
  async list(query = {}) {
    const qs = toQueryString(query);
    return apiFetch(`/schedules${qs}`);
  },

  /**
   * GET /schedules/{scheduleId}
   * @param {string} id
   * @returns {Promise<Types.GetScheduleResponse>}
   */
  async get(id) {
    return apiFetch(`/schedules/${id}`);
  },

  /**
   * PATCH /schedules/{scheduleId}
   * @param {string} id
   * @param {Types.UpdateScheduleRequest} body
   * @returns {Promise<Types.UpdateScheduleResponse>}
   */
  async update(id, body) {
    return apiFetch(`/schedules/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    });
  },

  /**
   * DELETE /schedules/{scheduleId}
   * @param {string} id
   * @returns {Promise<void>}
   */
  async delete(id) {
    return apiFetch(`/schedules/${id}`, { method: 'DELETE' });
  },
};

// ================================================================
//  §7 경로 (Route)
// ================================================================

const route = {
  /**
   * GET /schedules/{scheduleId}/route
   * @param {string} scheduleId
   * @param {Types.GetRouteQuery} [query]
   * @returns {Promise<Types.GetRouteResponse>}
   */
  async get(scheduleId, query = {}) {
    const qs = toQueryString(query);
    return apiFetch(`/schedules/${scheduleId}/route${qs}`);
  },
};

// ================================================================
//  §8 푸시 알림 (Push)
// ================================================================

const push = {
  /**
   * POST /push/subscribe
   * @param {Types.PushSubscribeRequest} body
   * @returns {Promise<Types.PushSubscribeResponse>}
   */
  async subscribe(body) {
    return apiFetch('/push/subscribe', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  /**
   * DELETE /push/subscribe/{subscriptionId}
   * @param {string} id
   * @returns {Promise<void>}
   */
  async unsubscribe(id) {
    return apiFetch(`/push/subscribe/${id}`, { method: 'DELETE' });
  },
};

// ================================================================
//  §9 장소 검색 (Geocode)
// ================================================================

const geocode = {
  /**
   * POST /geocode
   * @param {Types.GeocodeRequest} body
   * @returns {Promise<Types.GeocodeResponse>}
   */
  async search(body) {
    return apiFetch('/geocode', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },
};

// ================================================================
//  유틸
// ================================================================

/**
 * 객체를 query string으로 변환 (null/undefined 제외)
 * @param {Object} params
 * @returns {string} - "?key=val&..." 또는 ""
 */
function toQueryString(params) {
  const entries = Object.entries(params).filter(([, v]) => v != null);
  if (entries.length === 0) return '';
  return '?' + new URLSearchParams(entries).toString();
}

// ================================================================
//  Export
// ================================================================

export const api = {
  auth,
  members,
  main,
  map,
  schedules,
  route,
  push,
  geocode,
};

export { tokenStorage } from './fetchClient.js';
export { ApiError, getErrorMessage } from './errors.js';
