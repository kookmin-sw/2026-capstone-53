/**
 * 오늘어디 — API 에러 처리
 * @module api/errors
 */

/** @typedef {import('../types/api').ErrorCode} ErrorCode */

// ================================================================
//  ApiError 클래스
// ================================================================

export class ApiError extends Error {
  /**
   * @param {ErrorCode} code
   * @param {string}    message
   * @param {number}    httpStatus
   * @param {Object|null} [details]
   */
  constructor(code, message, httpStatus, details = null) {
    super(message);
    this.name = 'ApiError';
    /** @type {ErrorCode} */
    this.code = code;
    /** @type {number} */
    this.httpStatus = httpStatus;
    /** @type {Object|null} */
    this.details = details;
  }
}

// ================================================================
//  ErrorCode → 사용자 메시지 매핑
// ================================================================

/** @type {Record<ErrorCode, string>} */
const ERROR_MESSAGES = {
  VALIDATION_ERROR:             '입력값을 확인해주세요.',
  INVALID_CREDENTIALS:          '아이디 또는 비밀번호가 일치하지 않아요.',
  TOKEN_EXPIRED:                '로그인이 만료되었어요. 다시 로그인해주세요.',
  UNAUTHORIZED:                 '로그인이 필요해요.',
  FORBIDDEN_RESOURCE:           '접근 권한이 없어요.',
  MEMBER_NOT_FOUND:             '회원 정보를 찾을 수 없어요.',
  SCHEDULE_NOT_FOUND:           '일정을 찾을 수 없어요.',
  ROUTE_NOT_CALCULATED:         '경로가 아직 계산되지 않았어요. 잠시 후 다시 시도해주세요.',
  GEOCODE_NO_MATCH:             '검색 결과가 없어요. 다른 키워드로 시도해주세요.',
  SUBSCRIPTION_NOT_FOUND:       '알림 구독 정보를 찾을 수 없어요.',
  LOGIN_ID_DUPLICATED:          '이미 사용 중인 아이디예요.',
  RESOURCE_NOT_FOUND:           '요청하신 페이지를 찾을 수 없어요.',
  EXTERNAL_ROUTE_API_FAILED:    '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
  EXTERNAL_AUTH_MISCONFIGURED:  '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
  MAP_PROVIDER_UNAVAILABLE:     '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
  INTERNAL_SERVER_ERROR:        '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
  EXTERNAL_TIMEOUT:             '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
};

/**
 * ErrorCode를 사용자 친화적 한국어 메시지로 변환
 * @param {ErrorCode|string} code
 * @returns {string}
 */
export function getErrorMessage(code) {
  return ERROR_MESSAGES[code] ?? '알 수 없는 오류가 발생했어요.';
}
