/** @typedef {import('../../types/api')} T */

// transferCount = 이용 대중교통 노선 수 (v1.1.20 정의). 환승 횟수 = Math.max(0, transferCount - 1).
/** @type {Record<string, { route: T.Route, calculatedAt: string }>} */
export const seedRoutes = {
  // 도보 + 버스 1노선 + 도보 → transferCount: 1 (환승 없음)
  'sch_01HSEED0001ABCDEFGHJKLMN': {
    route: {
      totalDurationMinutes: 35,
      totalDistanceMeters:  8500,
      totalWalkMeters:      700,
      transferCount:        1,
      payment:              1450,
      segments: [
        {
          mode: 'WALK',
          durationMinutes: 5,
          distanceMeters:  350,
          path: [[127.012, 37.661], [127.013, 37.662]],
        },
        {
          mode: 'BUS',
          durationMinutes: 25,
          distanceMeters:  7500,
          from:         '국민대 앞',
          to:           '국민대학교후문',
          lineName:     '153',
          lineId:       '100100153',
          path: [[127.013, 37.662], [127.015, 37.662], [126.999, 37.613]],
        },
        {
          mode: 'WALK',
          durationMinutes: 5,
          distanceMeters:  350,
          path: [[126.999, 37.613], [126.997, 37.610]],
        },
      ],
    },
    calculatedAt: '2026-04-21T08:25:00+09:00',
  },

  // 도보 + 지하철 2노선(4호선→2호선) + 도보 → transferCount: 2 (환승 1회)
  'sch_01HSEED0002ABCDEFGHJKLMN': {
    route: {
      totalDurationMinutes: 55,
      totalDistanceMeters:  22000,
      totalWalkMeters:      900,
      transferCount:        2,
      payment:              1450,
      segments: [
        {
          mode: 'WALK',
          durationMinutes: 5,
          distanceMeters:  400,
          path: [[127.012, 37.661], [127.011, 37.659]],
        },
        {
          mode: 'SUBWAY',
          durationMinutes: 42,
          distanceMeters:  21000,
          from:         '수유역',
          to:           '강남역',
          lineName:     '4호선 → 2호선',
          lineId:       '4',
          stationStart: '수유역',
          stationEnd:   '강남역',
          stationCount: 18,
          path: [[127.011, 37.659], [127.028, 37.498]],
        },
        {
          mode: 'WALK',
          durationMinutes: 8,
          distanceMeters:  500,
          path: [[127.028, 37.498], [127.028, 37.497]],
        },
      ],
    },
    calculatedAt: '2026-04-20T15:10:00+09:00',
  },
};
