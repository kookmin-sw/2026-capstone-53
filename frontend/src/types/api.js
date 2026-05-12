/**
 * 오늘어디 백엔드 API 타입 정의
 * 기반: backend/docs/api-spec.md (feat/backend)
 *
 * JS 프로젝트이므로 JSDoc으로 작성.
 * IDE 자동완성 및 타입 힌트 지원.
 */

// ================================================================
//  §11 데이터 타입 부록
// ================================================================

/**
 * @typedef {'NAVER' | 'KAKAO' | 'ODSAY' | 'MANUAL'} PlaceProvider
 */

/**
 * @typedef {Object} Place
 * @property {string}          name
 * @property {number}          lat
 * @property {number}          lng
 * @property {string}          [address]
 * @property {string}          [placeId]
 * @property {PlaceProvider}   [provider]
 */

/**
 * @typedef {'ONCE' | 'DAILY' | 'WEEKLY' | 'CUSTOM'} RoutineType
 * @typedef {'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'} DayOfWeek
 */

/**
 * @typedef {Object} RoutineRule
 * @property {RoutineType}   type
 * @property {DayOfWeek[]}   [daysOfWeek]
 * @property {number}        [intervalDays]
 */

/**
 * @typedef {'EARLIER' | 'ON_TIME' | 'LATER'} DepartureAdvice
 * @typedef {'CALCULATED' | 'PENDING_RETRY'}   RouteStatus
 */

/**
 * @typedef {Object} Schedule
 * @property {string}              scheduleId
 * @property {string}              title
 * @property {Place}               origin
 * @property {Place}               destination
 * @property {string}              userDepartureTime          - ISO datetime
 * @property {string}              arrivalTime                - ISO datetime
 * @property {number|null}         estimatedDurationMinutes
 * @property {string|null}         recommendedDepartureTime   - ISO datetime
 * @property {DepartureAdvice|null} departureAdvice
 * @property {number}              reminderOffsetMinutes
 * @property {string|null}         reminderAt                 - ISO datetime
 * @property {RoutineRule|null}    routineRule
 * @property {RouteStatus}         routeStatus
 * @property {string|null}         routeCalculatedAt          - ISO datetime
 * @property {string}              createdAt                  - ISO datetime
 */

/**
 * @typedef {'WALK' | 'BUS' | 'SUBWAY'} SegmentMode
 */

/**
 * @typedef {Object} RouteSegment
 * @property {SegmentMode}         mode
 * @property {number}              durationMinutes
 * @property {number}              distanceMeters
 * @property {string}              [from]
 * @property {string}              [to]
 * @property {string}              [lineName]
 * @property {string}              [lineId]
 * @property {string}              [stationStart]
 * @property {string}              [stationEnd]
 * @property {number}              [stationCount]
 * @property {[number, number][]}  path               - [lng, lat][]
 */

/**
 * @typedef {Object} Route
 * @property {number}            totalDurationMinutes
 * @property {number}            totalDistanceMeters
 * @property {number}            totalWalkMeters
 * @property {number}            transferCount
 * @property {number}            payment
 * @property {RouteSegment[]}    segments
 */

/**
 * @template T
 * @typedef {Object} ApiResponse
 * @property {T} data
 */

/**
 * @typedef {Object} ApiError
 * @property {{ code: ErrorCode, message: string, details: Object|null }} error
 */

// ================================================================
//  에러 코드
// ================================================================

/**
 * @typedef {'VALIDATION_ERROR'
 *   | 'INVALID_CREDENTIALS'
 *   | 'TOKEN_EXPIRED'
 *   | 'UNAUTHORIZED'
 *   | 'FORBIDDEN_RESOURCE'
 *   | 'MEMBER_NOT_FOUND'
 *   | 'SCHEDULE_NOT_FOUND'
 *   | 'ROUTE_NOT_CALCULATED'
 *   | 'GEOCODE_NO_MATCH'
 *   | 'SUBSCRIPTION_NOT_FOUND'
 *   | 'LOGIN_ID_DUPLICATED'
 *   | 'EXTERNAL_ROUTE_API_FAILED'
 *   | 'EXTERNAL_AUTH_MISCONFIGURED'
 *   | 'MAP_PROVIDER_UNAVAILABLE'
 *   | 'RESOURCE_NOT_FOUND'
 *   | 'INTERNAL_SERVER_ERROR'
 *   | 'EXTERNAL_TIMEOUT'
 * } ErrorCode
 */

// ================================================================
//  §3 인증 (Auth)
// ================================================================

/**
 * POST /auth/signup
 * @typedef {Object} SignupRequest
 * @property {string} loginId
 * @property {string} password
 * @property {string} nickname
 */

/**
 * @typedef {Object} SignupResponse
 * @property {string} memberId
 * @property {string} loginId
 * @property {string} nickname
 * @property {string} accessToken
 * @property {string} refreshToken
 */

/**
 * POST /auth/login
 * @typedef {Object} LoginRequest
 * @property {string} loginId
 * @property {string} password
 */

/**
 * @typedef {Object} LoginResponse
 * @property {string} memberId
 * @property {string} accessToken
 * @property {string} refreshToken
 */

/**
 * POST /auth/logout
 * @typedef {Object} LogoutRequest
 * @property {string} refreshToken
 */
// Response: 204 No Content

// ================================================================
//  §4 회원 (Member)
// ================================================================

/**
 * GET /members/me
 * @typedef {Object} GetMyInfoResponse
 * @property {string} memberId
 * @property {string} loginId
 * @property {string} nickname
 * @property {string} createdAt   - ISO datetime
 */

/**
 * PATCH /members/me
 * @typedef {Object} UpdateMyInfoRequest
 * @property {string} [nickname]
 * @property {string} [password]
 */

/**
 * @typedef {GetMyInfoResponse} UpdateMyInfoResponse
 */
// DELETE /members/me → 204 No Content

// ================================================================
//  §5 메인·지도 (Display)
// ================================================================

/**
 * GET /main?lat=&lng=
 * @typedef {Object} GetMainQuery
 * @property {number} [lat]
 * @property {number} [lng]
 */

/**
 * @typedef {Object} NearestSchedule
 * @property {string}      scheduleId
 * @property {string}      title
 * @property {string}      arrivalTime                - ISO datetime
 * @property {Place}       origin
 * @property {Place}       destination
 * @property {boolean}     hasCalculatedRoute
 * @property {string|null} recommendedDepartureTime   - ISO datetime
 * @property {string|null} reminderAt                 - ISO datetime
 */

/**
 * @typedef {Object} GetMainResponse
 * @property {NearestSchedule|null} nearestSchedule
 * @property {{ lat: number, lng: number }} mapCenter
 */

/**
 * GET /map/config
 * @typedef {Object} GetMapConfigResponse
 * @property {string} provider
 * @property {number} defaultZoom
 * @property {{ lat: number, lng: number }} defaultCenter
 * @property {string} tileStyle
 */

// ================================================================
//  §6 일정 (Schedule)
// ================================================================

/**
 * POST /schedules
 * @typedef {Object} CreateScheduleRequest
 * @property {string}        title
 * @property {Place}         origin
 * @property {Place}         destination
 * @property {string}        userDepartureTime    - ISO datetime
 * @property {string}        arrivalTime          - ISO datetime
 * @property {number}        [reminderOffsetMinutes]
 * @property {RoutineRule}   [routineRule]
 */

/**
 * @typedef {Schedule} CreateScheduleResponse
 */

/**
 * GET /schedules?from=&to=&limit=&cursor=
 * @typedef {Object} GetSchedulesQuery
 * @property {string} [from]     - ISO date
 * @property {string} [to]       - ISO date
 * @property {number} [limit]
 * @property {string} [cursor]
 */

/**
 * @typedef {Object} GetSchedulesResponse
 * @property {Schedule[]}    items
 * @property {string|null}   nextCursor
 * @property {boolean}       hasMore
 */

/**
 * GET /schedules/{scheduleId}
 * @typedef {Schedule} GetScheduleResponse
 */

/**
 * PATCH /schedules/{scheduleId}
 * @typedef {Object} UpdateScheduleRequest
 * @property {string}        [title]
 * @property {Place}         [origin]
 * @property {Place}         [destination]
 * @property {string}        [userDepartureTime]
 * @property {string}        [arrivalTime]
 * @property {number}        [reminderOffsetMinutes]
 * @property {RoutineRule}   [routineRule]
 */

/**
 * @typedef {Schedule} UpdateScheduleResponse
 */

// DELETE /schedules/{scheduleId} → 204 No Content

// ================================================================
//  §7 경로 (Route)
// ================================================================

/**
 * GET /schedules/{scheduleId}/route?forceRefresh=
 * @typedef {Object} GetRouteQuery
 * @property {boolean} [forceRefresh]
 */

/**
 * @typedef {Object} GetRouteResponse
 * @property {string} scheduleId
 * @property {Route}  route
 * @property {string} calculatedAt   - ISO datetime
 */

// ================================================================
//  §8 푸시 알림 (Push)
// ================================================================

/**
 * POST /push/subscribe
 * @typedef {Object} PushSubscribeRequest
 * @property {string} endpoint
 * @property {{ p256dh: string, auth: string }} keys
 */

/**
 * @typedef {Object} PushSubscribeResponse
 * @property {string} subscriptionId
 */

// DELETE /push/subscribe/{subscriptionId} → 204 No Content

// ================================================================
//  §9 장소 검색 (Geocode)
// ================================================================

/**
 * POST /geocode
 * @typedef {Object} GeocodeRequest
 * @property {string} query
 */

/**
 * @typedef {Object} GeocodeResponse
 * @property {boolean} matched
 * @property {string}  name
 * @property {string}  address
 * @property {number}  lat
 * @property {number}  lng
 * @property {string}  placeId
 * @property {string}  provider
 */

export {};
