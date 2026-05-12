/**
 * MSW 시나리오 토글
 *
 * 사용법:
 *   localStorage.setItem('msw-scenario', 'token-expired')
 *   → 새로고침 후 반영
 *
 * 시나리오 목록:
 *   'default'                — 정상 응답
 *   'route-pending-retry'    — POST /schedules → routeStatus PENDING_RETRY
 *   'external-route-failed'  — GET /schedules/{id}/route → 502
 *   'external-timeout'       — 모든 외부 API → 504
 *   'token-expired'          — 모든 인증 호출 → 401 TOKEN_EXPIRED
 */

export function getScenario() {
  return localStorage.getItem('msw-scenario') || 'default';
}

export function isScenario(name) {
  return getScenario() === name;
}
