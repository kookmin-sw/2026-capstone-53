// ============================================
// mockData.js — 신 API 명세서 v0.3 기반
// 혼잡도/추천 제거, 루틴+리마인더 포커스
// ============================================


// ── 1. 인증 (Auth) ──────────────────────────────

// POST /auth/signup 응답
export const mockSignupResponse = {
  data: {
    memberId: "mem_01HAA7XBKE2M3N4P5Q6R7S8T9U",
    loginId: "chaeyeon01",
    nickname: "채연",
    accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    refreshToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
};

// POST /auth/login 응답
export const mockLoginResponse = {
  data: {
    memberId: "mem_01HAA7XBKE2M3N4P5Q6R7S8T9U",
    accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    refreshToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
};


// ── 2. 회원 정보 (Member) ──────────────────────

// GET /members/me 응답
export const mockMember = {
  data: {
    memberId: "mem_01HAA7XBKE2M3N4P5Q6R7S8T9U",
    loginId: "chaeyeon01",
    nickname: "채연",
    preferences: {
      maxWalkingDistance: 500,
      transferSensitivity: "MEDIUM",
      priorityWeight: 3
    },
    createdAt: "2026-04-20T10:00:00+09:00"
  }
};


// ── 3. 선호도 (Preferences) ────────────────────

// PUT /members/me/preferences 요청 및 응답
export const mockPreferences = {
  maxWalkingDistance: 500,
  transferSensitivity: "MEDIUM",
  priorityWeight: 3,
  preferSeat: true
};


// ── 4. 메인 화면 (Main) ────────────────────────

// GET /main 응답
export const mockMainData = {
  data: {
    nearestSchedule: {
      scheduleId: "sch_01HBB3XYZKE2M3N4P5Q6R7S8",
      title: "등교",
      arrivalTime: "2026-04-21T09:00:00+09:00",
      origin: {
        name: "집",
        lat: 37.66,
        lng: 127.01
      },
      destination: {
        name: "국민대학교",
        lat: 37.61,
        lng: 126.99
      },
      hasPrecalculatedRoute: true,
      reminderAt: "2026-04-21T08:30:00+09:00",
      averageDurationMinutes: 42,
      reminderOffsetMinutes: 30,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"]
      }
    },
    mapCenter: {
      lat: 37.66,
      lng: 127.01
    }
  }
};

// 일정이 없을 때
export const mockMainDataEmpty = {
  data: {
    nearestSchedule: null,
    mapCenter: {
      lat: 37.5665,
      lng: 126.9780
    }
  }
};


// ── 5. 일정 / 루틴 (Schedules) ─────────────────

// POST /schedules 요청
export const mockScheduleCreateRequest = {
  title: "국민대 등교",
  origin: { name: "우이동", lat: 37.66, lng: 127.01 },
  destination: { name: "국민대학교", lat: 37.61, lng: 126.99 },
  arrivalTime: "2026-04-21T09:00:00+09:00",
  reminderOffsetMinutes: 30,
  routineRule: {
    type: "WEEKLY",
    daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"]
  }
};

// POST /schedules 응답
export const mockScheduleCreateResponse = {
  data: {
    scheduleId: "sch_01HBB3XYZKE2M3N4P5Q6R7S8",
    averageDurationMinutes: 42,
    reminderAt: "2026-04-21T08:30:00+09:00",
    nextOccurrence: "2026-04-21T09:00:00+09:00"
  }
};

// GET /schedules 응답
export const mockScheduleList = {
  data: [
    {
      scheduleId: "sch_01HBB3XYZKE2M3N4P5Q6R7S8",
      title: "국민대 등교",
      origin: { name: "우이동" },
      destination: { name: "국민대학교" },
      arrivalTime: "2026-04-21T09:00:00+09:00",
      reminderAt: "2026-04-21T08:30:00+09:00",
      averageDurationMinutes: 42,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"]
      }
    },
    {
      scheduleId: "sch_02HCC4ABCKE3N4P5Q6R7S8T9",
      title: "토익 학원",
      origin: { name: "우이동" },
      destination: { name: "강남역 토익학원" },
      arrivalTime: "2026-04-26T14:00:00+09:00",
      reminderAt: "2026-04-26T13:00:00+09:00",
      averageDurationMinutes: 55,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["SAT"]
      }
    },
    {
      scheduleId: "sch_03HDD5ABCKE4N5P6Q7R8S9T0",
      title: "헬스장",
      origin: { name: "우이동" },
      destination: { name: "쌍문동 피트니스센터" },
      arrivalTime: "2026-04-28T07:00:00+09:00",
      reminderAt: "2026-04-28T06:30:00+09:00",
      averageDurationMinutes: 20,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["MON", "WED", "FRI"]
      }
    },
    {
      scheduleId: "sch_04HEE6ABCKE5N6P7Q8R9S0T1",
      title: "스터디",
      origin: { name: "국민대학교" },
      destination: { name: "혜화역 스터디카페" },
      arrivalTime: "2026-04-22T18:00:00+09:00",
      reminderAt: "2026-04-22T17:15:00+09:00",
      averageDurationMinutes: 35,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["TUE"]
      }
    },
    {
      scheduleId: "sch_05HFF7ABCKE6N7P8Q9R0S1T2",
      title: "병원 진료",
      origin: { name: "우이동" },
      destination: { name: "수유역 내과의원" },
      arrivalTime: "2026-04-24T10:30:00+09:00",
      reminderAt: "2026-04-24T10:00:00+09:00",
      averageDurationMinutes: 18,
      routineRule: null
    },
    {
      scheduleId: "sch_06HGG8ABCKE7N8P9Q0R1S2T3",
      title: "동아리 모임",
      origin: { name: "국민대학교" },
      destination: { name: "홍대입구역 카페" },
      arrivalTime: "2026-04-25T19:00:00+09:00",
      reminderAt: "2026-04-25T18:00:00+09:00",
      averageDurationMinutes: 48,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["FRI"]
      }
    },
    {
      scheduleId: "sch_07HHH9ABCKE8N9P0Q1R2S3T4",
      title: "알바",
      origin: { name: "우이동" },
      destination: { name: "수유역 편의점" },
      arrivalTime: "2026-04-27T15:00:00+09:00",
      reminderAt: "2026-04-27T14:30:00+09:00",
      averageDurationMinutes: 15,
      routineRule: {
        type: "WEEKLY",
        daysOfWeek: ["SUN", "WED"]
      }
    }
  ],
  meta: { totalElements: 7 }
};

// GET /schedules/{scheduleId} 응답
export const mockScheduleDetail = {
  data: {
    scheduleId: "sch_01HBB3XYZKE2M3N4P5Q6R7S8",
    title: "국민대 등교",
    origin: {
      name: "우이동",
      lat: 37.66,
      lng: 127.01,
      address: "서울 강북구 우이동",
      placeId: "12345",
      provider: "KAKAO"
    },
    destination: {
      name: "국민대학교",
      lat: 37.61,
      lng: 126.99,
      address: "서울 성북구 정릉로 77",
      placeId: "67890",
      provider: "KAKAO"
    },
    arrivalTime: "2026-04-21T09:00:00+09:00",
    reminderAt: "2026-04-21T08:30:00+09:00",
    reminderOffsetMinutes: 30,
    averageDurationMinutes: 42,
    routineRule: {
      type: "WEEKLY",
      daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"]
    },
    createdAt: "2026-04-20T22:30:00+09:00",
    updatedAt: "2026-04-20T22:30:00+09:00"
  }
};


// ── 6. 경로 (Routes) ──────────────────────────

// GET /schedules/{id}/routes 응답
export const mockRouteData = {
  data: {
    scheduleId: "sch_01HBB3XYZKE2M3N4P5Q6R7S8",
    candidates: [
      {
        candidateId: "cand_01",
        rank: 1,
        totalDurationMinutes: 30,
        totalTransfers: 0,
        overallCongestion: "LOW",
        rankingScore: 85.2,
        segments: [
          {
            mode: "WALK",
            durationMinutes: 5,
            distanceMeters: 350,
            from: "우이동 집",
            to: "국민대앞",
            path: [
              [127.0120, 37.6610],
              [127.0105, 37.6585],
              [127.0080, 37.6555],
              [127.0055, 37.6520],
              [127.0025, 37.6478],
              [126.9998, 37.6435],
              [126.9985, 37.6390],
              [126.9975, 37.6345],
              [126.9970, 37.6295],
              [126.9968, 37.6248],
              [126.9965, 37.6200],
              [126.9960, 37.6158],
              [126.9955, 37.6125],
              [126.9950, 37.6100],
              [126.9950, 37.6092]
            ]
          },
          {
            mode: "BUS",
            durationMinutes: 25,
            lineName: "1711",
            from: "국민대앞",
            to: "국민대학교",
            path: [
              [126.9950, 37.6092],
              [126.9952, 37.6094],
              [126.9955, 37.6097],
              [126.9958, 37.6099],
              [126.9962, 37.6101],
              [126.9965, 37.6102],
              [126.9969, 37.6103]
            ]
          }
        ]
      }
    ],
    calculatedAt: "2026-04-21T08:25:00+09:00"
  }
};


// ── 7. 지오코딩 (Geocode) ──────────────────────

// POST /geocode 응답
export const mockGeocodeResponse = {
  data: {
    matched: true,
    name: "국민대학교",
    address: "서울 성북구 정릉로 77",
    lat: 37.6103,
    lng: 126.9969,
    placeId: "1234567",
    provider: "KAKAO_LOCAL"
  }
};


// ── 8. 지도 설정 (Map Config) ─────────────────

// GET /map/config 응답
export const mockMapConfig = {
  data: {
    provider: "KAKAO",
    defaultZoom: 15,
    defaultCenter: {
      lat: 37.5665,
      lng: 126.9780
    },
    tileStyle: "basic"
  }
};


// ── 9. 하위 호환 어댑터 (Legacy shape) ─────────
// Home.js, HomeV2.jsx, ScheduleCard, RouteCard 에서 사용
// API 명세 v0.3 RouteCandidate → 컴포넌트 props 변환

const _cand    = mockRouteData.data.candidates[0];  // candidateId, rank, totalDurationMinutes, totalTransfers, overallCongestion, rankingScore, segments
const _walkSeg = _cand.segments[0];   // WALK: from "우이동 집" → to "국민대앞" (5분, 350m)
const _busSeg  = _cand.segments[1];   // BUS: 1711, "국민대앞" → "국민대학교" (25분)

export const mockRouteInfo = {
  schedule: {
    title:           mockMainData.data.nearestSchedule.title,
    arrivalTime:     mockMainData.data.nearestSchedule.arrivalTime.substring(11, 16),
    originName:      mockMainData.data.nearestSchedule.origin.name,
    destinationName: mockMainData.data.nearestSchedule.destination.name,
    repeatDays:      mockMainData.data.nearestSchedule.routineRule?.daysOfWeek ?? [],
  },
  segments: _cand.segments,
  route: {
    boardingStop: {
      stopName:              _walkSeg.to,
      walkingTimeSeconds:    _walkSeg.durationMinutes * 60,
      walkingDistanceMeters: _walkSeg.distanceMeters,
      coordinates: {
        lat: _walkSeg.path[_walkSeg.path.length - 1][1],
        lng: _walkSeg.path[_walkSeg.path.length - 1][0],
      },
    },
    busRoute: {
      routeName:         _busSeg.lineName,
      busArrivalMinutes: 3,
      busTripMinutes:    _busSeg.durationMinutes,
    },
    totalTripMinutes: _cand.totalDurationMinutes,
    transferCount:    _cand.totalTransfers,
  },
  departureInfo: {
    recommendedDepartureTime: mockMainData.data.nearestSchedule.reminderAt.substring(11, 16),
    bufferMinutes: mockMainData.data.nearestSchedule.reminderOffsetMinutes,
  },
  weather: {
    temperature: 18,
    condition: 'CLEAR',
  },
  routeStops: [
    { stopName: _walkSeg.to, type: 'BOARDING' },
    { stopName: _busSeg.to,  type: 'NORMAL'   },
  ],
};

// Calendar.js 표시용 — GET /schedules 응답(API 명세 v0.3 §5.2) → Calendar display shape
export const mockSchedules = mockScheduleList.data.map(s => ({
  scheduleId:             s.scheduleId,
  title:                  s.title,
  originName:             s.origin.name,
  originCoordinates:      s.origin.lat != null ? { lat: s.origin.lat, lng: s.origin.lng } : null,
  destinationName:        s.destination.name,
  destinationCoordinates: s.destination.lat != null ? { lat: s.destination.lat, lng: s.destination.lng } : null,
  arrivalTime:            s.arrivalTime.substring(11, 16),   // "09:00" 표시용
  departureTime:          s.reminderAt.substring(11, 16),    // "08:30" (= reminderAt)
  repeatDays:             s.routineRule?.daysOfWeek ?? [],
  routineRule:            s.routineRule ?? null,
  startDate:              s.arrivalTime.substring(0, 10),    // "2026-04-21"
  endDate:                null,
  status:                 'ACTIVE',
  averageDurationMinutes: s.averageDurationMinutes,
}));

// 오늘의 전체 일정 타임라인용
export const mockTodaySchedules = [
  {
    scheduleId: 'sch-today-001',
    title: '헬스장',
    originName: '우이동',
    destinationName: '쌍문동 피트니스센터',
    departureTime: '06:30',
    arrivalTime: '07:00',
    status: 'PAST',
  },
  {
    scheduleId: 'sch-today-002',
    title: '국민대 등교',
    originName: '우이동',
    destinationName: '국민대학교',
    departureTime: '08:30',
    arrivalTime: '09:00',
    status: 'NEXT',
  },
  {
    scheduleId: 'sch-today-003',
    title: '동아리 모임',
    originName: '국민대학교',
    destinationName: '홍대입구역 카페',
    departureTime: '18:00',
    arrivalTime: '19:00',
    status: 'UPCOMING',
  },
];

// 홈 화면 다중 일정 카드용
export const mockRouteInfoList = [
  mockRouteInfo,
  {
    schedule: {
      title:           '토익 학원',
      arrivalTime:     '14:00',
      originName:      '우이동',
      destinationName: '강남역 토익학원',
      repeatDays:      ['MON', 'WED', 'FRI'],
    },
    segments: [
      {
        mode: 'WALK',
        durationMinutes: 8,
        distanceMeters: 550,
        from: '우이동 집',
        to: '길음역',
        path: [
          [127.0120, 37.6610],
          [127.0150, 37.6560],
          [127.0180, 37.6510],
          [127.0210, 37.6460],
          [127.0240, 37.6410],
          [127.0260, 37.6360],
          [127.0260, 37.6300],
        ],
      },
      {
        mode: 'BUS',
        durationMinutes: 45,
        lineName: '143',
        from: '길음역',
        to: '강남역',
        path: [
          [127.0260, 37.6300],
          [127.0270, 37.6200],
          [127.0280, 37.6100],
          [127.0290, 37.5990],
          [127.0290, 37.5880],
          [127.0285, 37.5770],
          [127.0280, 37.5650],
          [127.0280, 37.4985],
        ],
      },
    ],
    route: {
      boardingStop: {
        stopName:              '길음역',
        walkingTimeSeconds:    480,
        walkingDistanceMeters: 550,
        coordinates: { lat: 37.6030, lng: 127.0260 },
      },
      busRoute: {
        routeName:         '143',
        busArrivalMinutes: 6,
        busTripMinutes:    45,
      },
      totalTripMinutes: 53,
      transferCount:    1,
    },
    departureInfo: {
      recommendedDepartureTime: '13:00',
      bufferMinutes: 7,
    },
    weather: { temperature: 18, condition: 'CLOUD' },
    routeStops: [
      { stopName: '길음역',    type: 'BOARDING' },
      { stopName: '미아사거리', type: 'NORMAL'   },
      { stopName: '강남역',    type: 'NORMAL'   },
    ],
  },
];

// Settings.js 가 기대하는 설정 형식
export const mockSettings = {
  maxWalkingDistanceMeters: mockPreferences.maxWalkingDistance,
  preference:               'FASTEST',
  stairAvoidance:           false,
  homeLocation: {
    name: mockMainData.data.nearestSchedule.origin.name,    // "우이동"
    coordinates: {
      lat: mockMainData.data.nearestSchedule.origin.lat,    // 37.66
      lng: mockMainData.data.nearestSchedule.origin.lng,    // 127.01
    },
  },
  alertEnabled:         true,
  alertBufferMinutes:   mockMainData.data.nearestSchedule.reminderOffsetMinutes, // 30
  feedbackAlertEnabled: false,
};


// ── 10. 장소 검색 mock (Geocode) ───────────────
// POST /geocode 연동 전 임시. 검색어가 키에 포함되면 반환.
// 실제 API 연동 시 src/api/client.js의 geocode() 함수만 교체하면 됨.
export const mockGeocodeResults = {
  '국민대': {
    name: '국민대학교',
    address: '서울 성북구 정릉로 77',
    lat: 37.6103,
    lng: 126.9969,
    placeId: '1234567',
    provider: 'KAKAO',
  },
  '우이동': {
    name: '우이동',
    address: '서울 강북구 우이동',
    lat: 37.6600,
    lng: 127.0100,
    placeId: '2345678',
    provider: 'KAKAO',
  },
  '강남역': {
    name: '강남역',
    address: '서울 강남구 강남대로 396',
    lat: 37.4980,
    lng: 127.0276,
    placeId: '3456789',
    provider: 'KAKAO',
  },
  '혜화역': {
    name: '혜화역',
    address: '서울 종로구 창경궁로 지하185',
    lat: 37.5822,
    lng: 127.0020,
    placeId: '4567890',
    provider: 'KAKAO',
  },
  '홍대': {
    name: '홍대입구역',
    address: '서울 마포구 양화로 지하160',
    lat: 37.5573,
    lng: 126.9248,
    placeId: '5678901',
    provider: 'KAKAO',
  },
  '수유역': {
    name: '수유역',
    address: '서울 강북구 도봉로 지하 327',
    lat: 37.6384,
    lng: 127.0253,
    placeId: '6789012',
    provider: 'KAKAO',
  },
  '길음역': {
    name: '길음역',
    address: '서울 성북구 도봉로 지하 271',
    lat: 37.6030,
    lng: 127.0259,
    placeId: '7890123',
    provider: 'KAKAO',
  },
};


// ── 11. 알림 내역 → src/data/notifications.js로 이동됨


// ── 헬퍼: 시간 포매터 ──────────────────────────

// ISO 8601 시간을 "08:30" 형태로 변환
export const formatTime = (isoString) => {
  const date = new Date(isoString);
  return date.toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
};

// ISO 8601 시간을 "4월 21일 화요일" 형태로 변환
export const formatDate = (isoString) => {
  const date = new Date(isoString);
  return date.toLocaleDateString("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "long"
  });
};

// 분을 "42분" 또는 "1시간 12분" 형태로 변환
export const formatDuration = (minutes) => {
  if (minutes < 60) return `${minutes}분`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m > 0 ? `${h}시간 ${m}분` : `${h}시간`;
};

// 남은 시간 계산 (리마인더까지)
export const getTimeUntil = (isoString) => {
  const target = new Date(isoString);
  const now = new Date();
  const diffMs = target - now;
  if (diffMs <= 0) return "지금 출발";
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 60) return `${diffMin}분 후`;
  const h = Math.floor(diffMin / 60);
  const m = diffMin % 60;
  return `${h}시간 ${m}분 후`;
};
