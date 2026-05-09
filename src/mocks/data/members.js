/** @typedef {import('../../types/api')} T */

/** @type {{ memberId: string, loginId: string, password: string, nickname: string, createdAt: string }} */
export const seedMember = {
  memberId: 'mem_01HSEED0001ABCDEFGHJKLMN',
  loginId:  'testuser',
  password: 'Test1234!',
  nickname: '테스트유저',
  createdAt: '2026-04-20T10:00:00+09:00',
};

// 발급된 토큰 (메모리 스토어)
export let activeTokens = {
  access:  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJtZW1fMDFIU0VFRCIsImlhdCI6MTcxNjAwMDAwMH0.mock-access',
  refresh: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJtZW1fMDFIU0VFRCIsImlhdCI6MTcxNjAwMDAwMH0.mock-refresh',
};

export function resetTokens() {
  activeTokens.access  = null;
  activeTokens.refresh = null;
}

export function issueTokens() {
  activeTokens.access  = `eyJ-access-${Date.now()}`;
  activeTokens.refresh = `eyJ-refresh-${Date.now()}`;
  return { ...activeTokens };
}
