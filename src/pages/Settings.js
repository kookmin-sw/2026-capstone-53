import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useTheme } from '../contexts/ThemeContext';
import { useSettings } from '../contexts/SettingsContext';
import { usePushNotification } from '../hooks/usePushNotification';
import { SettingsSkeletons, ErrorState } from '../components/StateUI';
import './Settings.css';

/* ================================================================
   로그아웃 확인 다이얼로그
   ================================================================ */
function LogoutDialog({ onConfirm, onCancel }) {
  return (
    <>
      <div className="st-dialog-backdrop" onClick={onCancel} />
      <div className="st-dialog">
        <div className="st-dialog__icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" stroke="#EF4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h3 className="st-dialog__title">로그아웃</h3>
        <p className="st-dialog__desc">정말 로그아웃하시겠습니까?</p>
        <div className="st-dialog__btns">
          <button className="st-dialog__btn st-dialog__btn--cancel" onClick={onCancel}>취소</button>
          <button className="st-dialog__btn st-dialog__btn--confirm" onClick={onConfirm}>로그아웃</button>
        </div>
      </div>
    </>
  );
}

/* ================================================================
   회원정보 수정 바텀시트
   ================================================================ */
function ProfileEditSheet({ member, onClose, onSaved, onPasswordChanged }) {
  const [nickname, setNickname]     = useState(member.nickname || '');
  const [curPw, setCurPw]           = useState('');
  const [newPw, setNewPw]           = useState('');
  const [newPwConfirm, setNewPwConfirm] = useState('');
  const [touched, setTouched]       = useState({});

  const pwFilled    = newPw.length > 0 || newPwConfirm.length > 0;
  const pwTooShort  = touched.newPw && newPw.length > 0 && newPw.length < 8;
  const pwMismatch  = touched.newPwConfirm && newPwConfirm.length > 0 && newPw !== newPwConfirm;

  const blur = (f) => setTouched(p => ({ ...p, [f]: true }));

  const canSave =
    nickname.trim().length > 0 &&
    (!pwFilled || (newPw.length >= 8 && newPw === newPwConfirm && curPw.length > 0));

  const handleSave = async () => {
    if (!canSave) return;
    try {
      const body = { nickname };
      if (pwFilled) body.password = newPw;
      await api.members.update(body);
      if (pwFilled) {
        onPasswordChanged();
      } else {
        onSaved();
      }
    } catch (err) {
      alert(err.message || '수정에 실패했어요');
    }
  };

  return (
    <>
      <div className="st-dialog-backdrop" onClick={onClose} />
      <div className="st-edit-sheet">
        <div className="st-edit-sheet__handle" />
        <h3 className="st-edit-sheet__title">회원정보 수정</h3>

        <div className="st-edit-sheet__body">
          {/* 닉네임 */}
          <div className="st-edit-field">
            <label className="st-edit-field__label">닉네임</label>
            <input
              className="st-edit-field__input"
              type="text"
              value={nickname}
              onChange={e => setNickname(e.target.value)}
              placeholder="닉네임"
            />
          </div>

          {/* 비밀번호 */}
          <div className="st-edit-field st-edit-field--mt">
            <label className="st-edit-field__label">비밀번호 변경</label>
            <p className="st-edit-field__hint">변경하지 않으려면 비워두세요</p>
            <div className="st-edit-field__inputs">
              <input
                className="st-edit-field__input"
                type="password"
                placeholder="현재 비밀번호"
                value={curPw}
                onChange={e => setCurPw(e.target.value)}
                autoComplete="current-password"
              />
              <input
                className={`st-edit-field__input${pwTooShort ? ' st-edit-field__input--error' : ''}`}
                type="password"
                placeholder="새 비밀번호 (8자 이상)"
                value={newPw}
                onChange={e => setNewPw(e.target.value)}
                onBlur={() => blur('newPw')}
                autoComplete="new-password"
              />
              {pwTooShort && <span className="st-edit-field__error">8자 이상 입력해주세요</span>}
              <input
                className={`st-edit-field__input${pwMismatch ? ' st-edit-field__input--error' : ''}`}
                type="password"
                placeholder="새 비밀번호 확인"
                value={newPwConfirm}
                onChange={e => setNewPwConfirm(e.target.value)}
                onBlur={() => blur('newPwConfirm')}
                autoComplete="new-password"
              />
              {pwMismatch && <span className="st-edit-field__error">비밀번호가 일치하지 않아요</span>}
            </div>
          </div>
        </div>

        <div className="st-edit-sheet__footer">
          <button className="st-edit-sheet__btn st-edit-sheet__btn--cancel" onClick={onClose}>취소</button>
          <button
            className={`st-edit-sheet__btn st-edit-sheet__btn--save${!canSave ? ' st-edit-sheet__btn--disabled' : ''}`}
            onClick={handleSave}
            disabled={!canSave}
          >
            저장
          </button>
        </div>
      </div>
    </>
  );
}

/* ================================================================
   공통 UI 컴포넌트
   ================================================================ */

function Toggle({ on, onChange, disabled }) {
  return (
    <button
      className={`st-toggle ${on ? 'st-toggle--on' : ''} ${disabled ? 'st-toggle--disabled' : ''}`}
      onClick={() => !disabled && onChange(!on)}
      role="switch"
      aria-checked={on}
    >
      <span className="st-toggle__thumb" />
    </button>
  );
}

function SettingRow({ label, desc, right, disabled, onClick }) {
  return (
    <div
      className={`st-row ${disabled ? 'st-row--disabled' : ''} ${onClick ? 'st-row--clickable' : ''}`}
      onClick={disabled ? undefined : onClick}
    >
      <div className="st-row__left">
        <span className="st-row__label">{label}</span>
        {desc && <span className="st-row__desc">{desc}</span>}
      </div>
      {right && <div className="st-row__right">{right}</div>}
    </div>
  );
}

function Section({ title, subtitle, children }) {
  return (
    <div className="st-section">
      <div className="st-section__head">
        <h2 className="st-section__title">{title}</h2>
        {subtitle && <p className="st-section__subtitle">{subtitle}</p>}
      </div>
      <div className="st-section__card">
        {children}
      </div>
    </div>
  );
}

function Divider() {
  return <div className="st-divider" />;
}

/* ================================================================
   Settings Page
   ================================================================ */
function Settings() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [showLogout, setShowLogout]   = React.useState(false);
  const [showDelete, setShowDelete]   = React.useState(false);
  const [showProfile, setShowProfile] = React.useState(false);
  const [toast, setToast]             = React.useState('');
  const [uiState, setUiState]         = React.useState('loading');
  const [member, setMember]           = React.useState(null);
  const { theme, toggleTheme } = useTheme();
  const { settings: cfg, updateSetting: update } = useSettings();
  const { permission: notifPermission } = usePushNotification();

  const fetchMember = React.useCallback(async () => {
    setUiState('loading');
    try {
      const data = await api.members.me();
      setMember(data);
      setUiState('ready');
    } catch (err) {
      console.error('[Settings] 회원 정보 로드 실패', err);
      setUiState('error');
    }
  }, []);

  useEffect(() => {
    const forced = searchParams.get('state');
    if (forced === 'loading') return;
    if (forced === 'error') { setUiState('error'); return; }
    fetchMember();
  }, [searchParams, fetchMember]);

  const retry = () => fetchMember();

  const handleLogout = async () => {
    try {
      await api.auth.logout();
    } catch (e) {
      console.warn('[Settings] 서버 로그아웃 실패, 클라이언트만 정리:', e);
    } finally {
      localStorage.clear();
      navigate('/login');
    }
  };

  const BUFFERS = [5, 10, 20, 30];

  const sendTestNotification = async () => {
    alert('1. 버튼 클릭됨');

    if (!('Notification' in window)) {
      alert('이 브라우저는 알림을 지원하지 않아요');
      return;
    }

    let permission = Notification.permission;
    alert('2. 권한 상태: ' + permission);

    if (permission === 'default') {
      permission = await Notification.requestPermission();
      alert('3. 요청 결과: ' + permission);
    }

    if (permission === 'granted') {
      new Notification('오늘어디', {
        body: '08:18에 출발하세요 (국민대 등교, 예상 42분)',
        icon: '/logo192.png',
      });
      alert('4. 알림 발송 완료');
    } else {
      alert('알림이 차단되어 있어요. 브라우저 설정에서 허용해주세요.');
    }
  };

  return (
    <div className="st-page">
      <div className="st-container">

        {uiState === 'loading' && <SettingsSkeletons />}
        {uiState === 'error'   && <ErrorState onRetry={retry} />}

        {uiState === 'ready' && (<>
          <h1 className="st-page-title">설정</h1>

          {/* ── 프로필 섹션 ── */}
          <div className="st-profile" onClick={() => setShowProfile(true)}>
            <div className="st-profile__info">
              <span className="st-profile__nickname">{member?.nickname}</span>
              <span className="st-profile__loginid">{member?.loginId}</span>
            </div>
            <div className="st-profile__actions">
              <button className="st-profile__action-btn" onClick={e => { e.stopPropagation(); setShowProfile(true); }}>
                수정
              </button>
              <button className="st-profile__action-btn st-profile__action-btn--danger" onClick={e => { e.stopPropagation(); handleLogout(); }}>
                로그아웃
              </button>
            </div>
          </div>
          <div className="st-divider st-divider--section" />

          {/* ── 섹션 1: 알림 ── */}
          <Section title="알림">

            {/* 알림 차단 경고 */}
            {notifPermission === 'denied' && (
              <div className="st-notif-blocked">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                  <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
                    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <line x1="12" y1="9" x2="12" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                  <line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                </svg>
                알림이 차단되어 있어요. 브라우저 설정에서 허용해주세요.
              </div>
            )}

            <SettingRow
              label="출발 알림"
              desc="추천 출발 시간에 맞춰 푸시 알림을 보내드려요"
              right={<Toggle on={cfg.alertEnabled} onChange={v => update('alertEnabled', v)} />}
            />

            <Divider />

            <div className={`st-buffer-group ${!cfg.alertEnabled ? 'st-buffer-group--disabled' : ''}`}>
              <div className="st-row">
                <div className="st-row__left">
                  <span className="st-row__label">여유 시간</span>
                  <span className="st-row__desc">여유 시간이 클수록 더 일찍 출발 알림을 받아요</span>
                </div>
              </div>
              <div className="st-chips">
                {BUFFERS.map(min => (
                  <button
                    key={min}
                    className={`st-chip ${cfg.alertBufferMinutes === min ? 'st-chip--on' : ''}`}
                    onClick={() => cfg.alertEnabled && update('alertBufferMinutes', min)}
                    disabled={!cfg.alertEnabled}
                  >
                    {min}분
                  </button>
                ))}
              </div>
            </div>

            {/* 테스트 알림 */}
            <Divider />
            <SettingRow
              label="테스트 알림 보내기"
              desc="알림이 잘 오는지 확인해보세요"
              onClick={sendTestNotification}
              right={
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path d="M9 18l6-6-6-6" stroke="#C5BFB8" strokeWidth="2.2"
                    strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              }
            />

          </Section>

          {/* ── 섹션 2: 디스플레이 ── */}
          <Section title="디스플레이">
            <SettingRow
              label="다크 모드"
              desc="어두운 배경으로 눈의 피로를 줄여드려요"
              right={<Toggle on={theme === 'dark'} onChange={() => toggleTheme()} />}
            />
          </Section>

          {/* ── 회원 탈퇴 ── */}
          <button
            className="st-withdraw"
            onClick={() => setShowDelete(true)}
          >
            회원 탈퇴
          </button>
        </>)}

      </div>

      {showLogout && (
        <LogoutDialog
          onConfirm={() => { setShowLogout(false); handleLogout(); }}
          onCancel={() => setShowLogout(false)}
        />
      )}

      {showProfile && (
        <ProfileEditSheet
          member={member}
          onClose={() => setShowProfile(false)}
          onSaved={() => {
            setShowProfile(false);
            setToast('수정 완료');
            setTimeout(() => setToast(''), 2000);
          }}
          onPasswordChanged={async () => {
            setShowProfile(false);
            alert('비밀번호가 변경되어 다시 로그인해주세요');
            try { await api.auth.logout(); } catch {}
            localStorage.clear();
            navigate('/login');
          }}
        />
      )}

      {toast && <div className="st-toast">{toast}</div>}

      {showDelete && (
        <>
          <div className="st-dialog-backdrop" onClick={() => setShowDelete(false)} />
          <div className="st-dialog">
            <div className="st-dialog__icon st-dialog__icon--danger">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <path d="M12 9v4M12 17h.01M3 12a9 9 0 1118 0 9 9 0 01-18 0z"
                  stroke="#EF4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
            <h3 className="st-dialog__title">회원 탈퇴</h3>
            <p className="st-dialog__desc">
              탈퇴하면 모든 일정과 데이터가 삭제되며<br/>복구할 수 없습니다. 정말 탈퇴하시겠습니까?
            </p>
            <div className="st-dialog__btns">
              <button className="st-dialog__btn st-dialog__btn--cancel" onClick={() => setShowDelete(false)}>취소</button>
              <button
                className="st-dialog__btn st-dialog__btn--confirm"
                onClick={async () => {
                  try {
                    await api.members.delete();
                  } catch (e) {
                    console.warn('[Settings] 회원 탈퇴 API 실패:', e);
                  } finally {
                    setShowDelete(false);
                    localStorage.clear();
                    navigate('/login');
                  }
                }}
              >
                탈퇴하기
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

export default Settings;
