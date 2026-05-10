/** @typedef {import('../../types/api')} T */

/** @type {{ memberId: string, loginId: string, password: string, nickname: string, createdAt: string }} */
export const seedMember = {
  memberId: 'mem_01HSEED0001ABCDEFGHJKLMN',
  loginId:  'testuser',
  password: 'Test1234!',
  nickname: '테스트유저',
  createdAt: '2026-04-20T10:00:00+09:00',
};

// 고정 시드 토큰 — 새로고침해도 항상 동일
export const SEED_ACCESS_TOKEN  = 'eyJtb2NrLWFjY2Vzcy10b2tlbi1zZWVkJ30';
export const SEED_REFRESH_TOKEN = 'eyJtb2NrLXJlZnJlc2gtdG9rZW4tc2VlZCJ9';
