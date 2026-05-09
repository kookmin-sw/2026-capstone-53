import { authHandlers } from './auth';
import { membersHandlers } from './members';
import { mainHandlers } from './main';
import { schedulesHandlers } from './schedules';
import { routeHandlers } from './route';
import { pushHandlers } from './push';
import { geocodeHandlers } from './geocode';

export const handlers = [
  ...authHandlers,
  ...membersHandlers,
  ...mainHandlers,
  ...schedulesHandlers,
  ...routeHandlers,
  ...pushHandlers,
  ...geocodeHandlers,
];
