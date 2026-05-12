import React, { useState, useCallback } from 'react';
import { api } from '../api';
import { ApiError, getErrorMessage } from '../api/errors';

// ================================================================
//  상수
// ================================================================

const EXPECTED_DOMAINS = {
  auth:      ['signup', 'login', 'logout'],
  members:   ['me', 'update', 'delete'],
  main:      ['get'],
  map:       ['config'],
  schedules: ['create', 'list', 'get', 'update', 'delete'],
  route:     ['get'],
  push:      ['subscribe', 'unsubscribe'],
  geocode:   ['search'],
};

const ALL_ERROR_CODES = [
  'VALIDATION_ERROR', 'INVALID_CREDENTIALS', 'TOKEN_EXPIRED', 'UNAUTHORIZED',
  'FORBIDDEN_RESOURCE', 'MEMBER_NOT_FOUND', 'SCHEDULE_NOT_FOUND',
  'ROUTE_NOT_CALCULATED', 'GEOCODE_NO_MATCH', 'SUBSCRIPTION_NOT_FOUND',
  'LOGIN_ID_DUPLICATED', 'EXTERNAL_ROUTE_API_FAILED',
  'EXTERNAL_AUTH_MISCONFIGURED', 'MAP_PROVIDER_UNAVAILABLE',
  'EXTERNAL_TIMEOUT', 'INTERNAL_SERVER_ERROR',
];

// ================================================================
//  스타일
// ================================================================

const S = {
  page: {
    maxWidth: 640, margin: '0 auto', padding: '24px 16px 80px',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Noto Sans KR", sans-serif',
    fontSize: 14, color: '#1a1a1a',
  },
  warn: {
    background: '#FEF3C7', border: '1px solid #F59E0B', borderRadius: 10,
    padding: '10px 14px', fontSize: 13, fontWeight: 600, color: '#92400E',
    marginBottom: 20, textAlign: 'center',
  },
  runAll: {
    width: '100%', padding: '12px', background: '#2563EB', color: '#fff',
    border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 600,
    cursor: 'pointer', marginBottom: 20,
  },
  card: (pass) => ({
    background: pass === true ? '#F0FDF4' : pass === false ? '#FEF2F2' : '#F9FAFB',
    border: `1px solid ${pass === true ? '#BBF7D0' : pass === false ? '#FECACA' : '#E5E7EB'}`,
    borderRadius: 12, padding: '16px', marginBottom: 14,
  }),
  title: { fontSize: 15, fontWeight: 700, marginBottom: 8 },
  btn: {
    padding: '8px 16px', background: '#374151', color: '#fff',
    border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 600,
    cursor: 'pointer', marginBottom: 10,
  },
  pre: {
    background: '#1F2937', color: '#D1D5DB', borderRadius: 8,
    padding: '10px 12px', fontSize: 11, overflow: 'auto',
    maxHeight: 240, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
  },
  table: { width: '100%', fontSize: 12, borderCollapse: 'collapse', marginTop: 8 },
  th: { textAlign: 'left', padding: '4px 8px', borderBottom: '1px solid #E5E7EB', fontWeight: 600 },
  td: { padding: '4px 8px', borderBottom: '1px solid #F3F4F6' },
};

// ================================================================
//  Test 1: API 객체 구조 검증
// ================================================================

function runTest1() {
  const missing = [];
  let fnCount = 0;

  for (const [domain, fns] of Object.entries(EXPECTED_DOMAINS)) {
    if (!api[domain]) {
      missing.push(`도메인 누락: ${domain}`);
      continue;
    }
    for (const fn of fns) {
      if (typeof api[domain][fn] !== 'function') {
        missing.push(`함수 누락: api.${domain}.${fn}`);
      } else {
        fnCount++;
      }
    }
  }

  const domainCount = Object.keys(EXPECTED_DOMAINS).filter(d => api[d]).length;
  const pass = domainCount === 8 && fnCount === 17;

  return {
    pass,
    detail: [
      `도메인: ${domainCount}/8`,
      `함수: ${fnCount}/17`,
      ...(missing.length ? ['', '누락 목록:', ...missing] : []),
    ].join('\n'),
  };
}

// ================================================================
//  Test 2: 게스트 호출
// ================================================================

async function runTest2() {
  localStorage.removeItem('accessToken');

  try {
    await api.map.config();
    return {
      pass: false,
      detail: '에러가 발생하지 않았습니다.\n(백엔드가 켜져 있으면 성공일 수 있음)',
    };
  } catch (err) {
    const isApiError = err instanceof ApiError;
    return {
      pass: isApiError,
      detail: [
        `ApiError 인스턴스: ${isApiError ? '✅' : '❌ (일반 Error)'}`,
        `code: ${err.code ?? '(없음)'}`,
        `message: ${err.message}`,
        `httpStatus: ${err.httpStatus ?? '(없음)'}`,
        '',
        '📋 Network 탭에서 확인:',
        '  1. http://localhost:8080/api/v1/map/config 요청이 나갔는지',
        '  2. Authorization 헤더가 없는지 (게스트 허용이라 정상)',
      ].join('\n'),
    };
  }
}

// ================================================================
//  Test 3: 인증 호출 (가짜 토큰)
// ================================================================

async function runTest3() {
  localStorage.setItem('accessToken', 'fake-token-smoke-test');

  // 401 시 리다이렉트 방지: 일시적으로 location.href setter 차단
  const origHref = Object.getOwnPropertyDescriptor(window, 'location')
    || Object.getOwnPropertyDescriptor(Window.prototype, 'location');
  let redirected = false;

  try {
    // href 변경을 감지만 하고 실제 이동은 막음
    delete window.location;
    window.location = new URL(document.location.href);
    Object.defineProperty(window.location, 'href', {
      set: (v) => { redirected = v; },
      get: () => document.location.href,
      configurable: true,
    });
  } catch {
    // 일부 브라우저에서 실패할 수 있음 — 무시
  }

  let result;
  try {
    await api.members.me();
    result = {
      pass: false,
      detail: '에러가 발생하지 않았습니다.\n(백엔드가 401을 반환하지 않았거나 연결 성공)',
    };
  } catch (err) {
    const isApiError = err instanceof ApiError;
    result = {
      pass: isApiError,
      detail: [
        `ApiError 인스턴스: ${isApiError ? '✅' : '❌'}`,
        `code: ${err.code ?? '(없음)'}`,
        `message: ${err.message}`,
        `httpStatus: ${err.httpStatus ?? '(없음)'}`,
        redirected ? `\n🔄 401 리다이렉트 감지: ${redirected}` : '',
        '',
        '📋 Network 탭에서 확인:',
        '  1. http://localhost:8080/api/v1/members/me 요청이 나갔는지',
        '  2. Authorization: Bearer fake-token-smoke-test 헤더가 있는지',
      ].join('\n'),
    };
  }

  // 정리
  localStorage.removeItem('accessToken');
  try {
    if (origHref) Object.defineProperty(window, 'location', origHref);
    else window.location = document.location;
  } catch { /* 복원 실패 무시 */ }

  return result;
}

// ================================================================
//  Test 4: ErrorCode 한국어 매핑
// ================================================================

function runTest4() {
  const rows = ALL_ERROR_CODES.map(code => {
    const msg = getErrorMessage(code);
    const ok = typeof msg === 'string' && msg.length > 0
      && !/^[a-zA-Z_]+$/.test(msg) && msg !== 'undefined';
    return { code, msg, ok };
  });

  const allOk = rows.every(r => r.ok);
  return { pass: allOk, rows };
}

// ================================================================
//  컴포넌트
// ================================================================

function TestCard({ title, index, result, onRun, children }) {
  const icon = result?.pass === true ? '✅' : result?.pass === false ? '❌' : '⬜';
  return (
    <div style={S.card(result?.pass)}>
      <div style={S.title}>{icon} Test {index}: {title}</div>
      <button style={S.btn} onClick={onRun}>실행</button>
      {result && !children && <pre style={S.pre}>{result.detail}</pre>}
      {result && children}
    </div>
  );
}

export default function SmokeTest() {
  const [results, setResults] = useState({});
  const [running, setRunning] = useState(false);

  const set = (key, val) => setResults(prev => ({ ...prev, [key]: val }));

  const run1 = useCallback(() => set('t1', runTest1()), []);
  const run2 = useCallback(async () => set('t2', await runTest2()), []);
  const run3 = useCallback(async () => set('t3', await runTest3()), []);
  const run4 = useCallback(() => set('t4', runTest4()), []);

  const runAll = async () => {
    setRunning(true);
    setResults({});
    run1();
    await run2();
    await run3();
    run4();
    setRunning(false);
  };

  const t4 = results.t4;

  return (
    <div style={S.page}>
      <div style={S.warn}>&#x26A0;&#xFE0F; 개발 전용 페이지 — 통합 완료 후 삭제 예정</div>

      <button style={{ ...S.runAll, opacity: running ? 0.6 : 1 }} onClick={runAll} disabled={running}>
        {running ? '실행 중...' : '전체 실행'}
      </button>

      <TestCard title="API 객체 구조 검증" index={1} result={results.t1} onRun={run1} />
      <TestCard title="게스트 호출 (토큰 없이)" index={2} result={results.t2} onRun={run2} />
      <TestCard title="인증 호출 (가짜 토큰)" index={3} result={results.t3} onRun={run3} />

      <TestCard title="ErrorCode 한국어 매핑" index={4} result={t4} onRun={run4}>
        {t4 && (
          <table style={S.table}>
            <thead>
              <tr>
                <th style={S.th}>코드</th>
                <th style={S.th}>메시지</th>
                <th style={S.th}></th>
              </tr>
            </thead>
            <tbody>
              {t4.rows.map(r => (
                <tr key={r.code}>
                  <td style={{ ...S.td, fontFamily: 'monospace', fontSize: 11 }}>{r.code}</td>
                  <td style={S.td}>{r.msg}</td>
                  <td style={{ ...S.td, textAlign: 'center' }}>{r.ok ? '✅' : '❌'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </TestCard>
    </div>
  );
}
