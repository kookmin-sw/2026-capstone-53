/**
 * 오늘어디 — 핵심 fetch 래퍼
 * @module api/fetchClient
 *
 * - Authorization 자동 첨부 (인증 불필요 경로 제외)
 * - 응답에서 data 자동 추출
 * - 401 → 토큰 제거 + /login 이동
 * - 502/503/504 → 서버 에러 메시지 변환
 * - 네트워크 에러 → 연결 확인 메시지
 */

import { ApiError, getErrorMessage } from './errors.js';

/** @typedef {import('../types/api').ApiError} ApiErrorResponse */

// ================================================================
//  Base URL
// ================================================================

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

// 토큰을 보내지 않는 경로
const NO_AUTH_PATHS = ['/auth/signup', '/auth/login', '/auth/logout'];

// ================================================================
//  토큰 관리
// ================================================================

export const tokenStorage = {
  getAccess:  () => localStorage.getItem('accessToken'),
  getRefresh: () => localStorage.getItem('refreshToken'),
  setTokens:  (access, refresh) => {
    localStorage.setItem('accessToken', access);
    if (refresh) localStorage.setItem('refreshToken', refresh);
  },
  clear: () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  },
};

// ================================================================
//  핵심 fetch 함수
// ================================================================

/**
 * @param {string} path       - /auth/login 등 BASE_URL 뒤 경로
 * @param {RequestInit & { skipAuth?: boolean }} [options]
 * @returns {Promise<any>}     - 성공 시 data 부분, 204 시 undefined
 */
export async function apiFetch(path, options = {}) {
  const { skipAuth, ...fetchOpts } = options;

  // ── 헤더 조립 ──
  const headers = { ...fetchOpts.headers };

  // body가 있고 FormData가 아니면 JSON
  if (fetchOpts.body && !(fetchOpts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  // 토큰 첨부 (NO_AUTH_PATHS 및 skipAuth 제외)
  if (!skipAuth && !NO_AUTH_PATHS.includes(path)) {
    const token = tokenStorage.getAccess();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  // ── fetch 실행 ──
  let res;
  try {
    res = await fetch(`${BASE_URL}${path}`, { ...fetchOpts, headers });
  } catch (networkErr) {
    throw new ApiError(
      'NETWORK_ERROR',
      '인터넷 연결을 확인해주세요.',
      0,
    );
  }

  // ── 204 No Content ──
  if (res.status === 204) {
    return undefined;
  }

  // ── 401 → 토큰 만료 / 미인증 ──
  if (res.status === 401) {
    tokenStorage.clear();
    window.location.href = '/login';
    // 반환 방지 (리다이렉트 후 코드 흐름 차단)
    return new Promise(() => {});
  }

  // ── JSON 파싱 ──
  let json;
  try {
    json = await res.json();
  } catch {
    throw new ApiError(
      'INTERNAL_SERVER_ERROR',
      '서버 응답을 처리할 수 없어요.',
      res.status,
    );
  }

  // ── 에러 응답 ──
  if (!res.ok) {
    const err = json?.error ?? {};
    const code = err.code ?? 'INTERNAL_SERVER_ERROR';

    // 502/503/504 → 통합 서버 에러 메시지
    if ([502, 503, 504].includes(res.status)) {
      throw new ApiError(
        code,
        '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
        res.status,
        err.details,
      );
    }

    throw new ApiError(
      code,
      getErrorMessage(code),
      res.status,
      err.details,
    );
  }

  // ── 성공: data 추출 ──
  return json.data;
}
