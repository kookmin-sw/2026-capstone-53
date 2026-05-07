import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { mockMember } from '../data/mockData';
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
  const [showLogout, setShowLogout] = React.useState(false);
  const [uiState, setUiState] = React.useState('loading');
  const { theme, toggleTheme } = useTheme();
  const { settings: cfg, updateSetting: update } = useSettings();
  const { permission: notifPermission } = usePushNotification();

  useEffect(() => {
    const forced = searchParams.get('state');
    if (forced === 'loading') return;
    if (forced === 'error') { setUiState('error'); return; }
    const t = setTimeout(() => setUiState('ready'), 1000);
    return () => clearTimeout(t);
  }, [searchParams]);

  const retry = () => {
    setUiState('loading');
    setTimeout(() => setUiState('ready'), 1000);
  };

  const handleLogout = () => {
    localStorage.clear();
    navigate('/login');
  };

  const handleLogoutConfirm = () => {
    setShowLogout(false);
    alert('로그아웃되었습니다.');
  };

  const BUFFERS = [5, 10, 20, 30];

  const sendTestNotification = () => {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    new Notification('오늘어디', {
      body: '08:18에 출발하세요 (국민대 등교, 예상 42분)',
      icon: '/logo192.png',
    });
  };

  return (
    <div className="st-page">
      <div className="st-container">

        {uiState === 'loading' && <SettingsSkeletons />}
        {uiState === 'error'   && <ErrorState onRetry={retry} />}

        {uiState === 'ready' && (<>
          <h1 className="st-page-title">설정</h1>

          {/* ── 프로필 섹션 ── */}
          <div className="st-profile">
            <div className="st-profile__info">
              <span className="st-profile__nickname">{mockMember.data.nickname}</span>
              <span className="st-profile__loginid">{mockMember.data.loginId}</span>
            </div>
            <div className="st-profile__actions">
              <button className="st-profile__action-btn" onClick={() => alert('비밀번호 변경')}>
                비밀번호 변경
              </button>
              <button className="st-profile__action-btn st-profile__action-btn--danger" onClick={handleLogout}>
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
            {notifPermission === 'granted' && (
              <>
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
              </>
            )}

          </Section>

          {/* ── 섹션 2: 디스플레이 ── */}
          <Section title="디스플레이">
            <SettingRow
              label="다크 모드"
              desc="어두운 배경으로 눈의 피로를 줄여드려요"
              right={<Toggle on={theme === 'dark'} onChange={() => toggleTheme()} />}
            />
          </Section>
        </>)}

      </div>

      {showLogout && (
        <LogoutDialog
          onConfirm={handleLogoutConfirm}
          onCancel={() => setShowLogout(false)}
        />
      )}
    </div>
  );
}

export default Settings;
