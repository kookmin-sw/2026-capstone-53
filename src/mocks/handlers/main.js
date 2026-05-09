import { http, HttpResponse } from 'msw';
import { seedSchedules } from '../data/schedules';

const API = 'http://localhost:8080/api/v1';

export const mainHandlers = [
  // GET /main (게스트 허용)
  http.get(`${API}/main`, () => {
    const sch = seedSchedules[0];
    return HttpResponse.json({
      data: {
        nearestSchedule: {
          scheduleId:               sch.scheduleId,
          title:                    sch.title,
          arrivalTime:              sch.arrivalTime,
          origin:                   sch.origin,
          destination:              sch.destination,
          hasCalculatedRoute:       sch.routeStatus === 'CALCULATED',
          recommendedDepartureTime: sch.recommendedDepartureTime,
          reminderAt:               sch.reminderAt,
        },
        mapCenter: { lat: 37.5665, lng: 126.9780 },
      },
    });
  }),

  // GET /map/config (게스트 허용)
  http.get(`${API}/map/config`, () => {
    return HttpResponse.json({
      data: {
        provider:      'NAVER',
        defaultZoom:   15,
        defaultCenter: { lat: 37.5665, lng: 126.9780 },
        tileStyle:     'basic',
      },
    });
  }),
];
