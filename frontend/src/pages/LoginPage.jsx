import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useTheme } from '../contexts/ThemeContext';
import logoTypo from '../assets/brand/logo-typo.svg';
import logoTypoWhite from '../assets/brand/logo-typo-white.svg';
import './AuthPage.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleLogin = async () => {
    if (!id || !pw) return;
    setError('');
    setSubmitting(true);
    try {
      await api.auth.login({ loginId: id, password: pw });
      localStorage.setItem('isLoggedIn', 'true');
      navigate('/');
    } catch (err) {
      if (err.code === 'INVALID_CREDENTIALS') {
        setError('아이디 또는 비밀번호가 올바르지 않아요');
      } else if (err.code === 'VALIDATION_ERROR') {
        setError('입력 정보를 확인해주세요');
      } else {
        setError('로그인 중 문제가 발생했어요. 잠시 후 다시 시도해주세요');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleLogin();
  };

  return (
    <div className="auth">
      <div className="auth__glow" />

      <div className="auth__card">
        {/* 로고 */}
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <img src={theme === 'dark' ? logoTypoWhite : logoTypo} alt="오늘어디" style={{ height: 48 }} />
        </div>
        <p className="auth__tagline">매일 함께하는 경로 알리미</p>

        {/* 입력 */}
        <div className="auth__fields">
          <input
            className="auth__input"
            type="text"
            placeholder="아이디"
            value={id}
            onChange={e => { setId(e.target.value); setError(''); }}
            onKeyDown={handleKeyDown}
            autoComplete="username"
          />
          <input
            className="auth__input"
            type="password"
            placeholder="비밀번호"
            value={pw}
            onChange={e => { setPw(e.target.value); setError(''); }}
            onKeyDown={handleKeyDown}
            autoComplete="current-password"
          />
        </div>

        {error && (
          <p style={{
            color: '#EF4444',
            fontSize: 13,
            fontWeight: 500,
            textAlign: 'center',
            margin: '12px 0 0',
            padding: '10px 14px',
            background: '#FEF2F2',
            borderRadius: 10,
          }}>
            {error}
          </p>
        )}

        {/* 로그인 버튼 */}
        <button
          className={`auth__btn${submitting ? ' auth__btn--disabled' : ''}`}
          onClick={handleLogin}
          disabled={submitting}
        >
          {submitting ? '로그인 중...' : '로그인'}
        </button>

        {/* 회원가입 링크 */}
        <p className="auth__footer">
          계정이 없으신가요?{' '}
          <button className="auth__link" onClick={() => navigate('/signup')}>
            회원가입
          </button>
        </p>
      </div>

      <p className="auth__copyright">© 2026 오늘어디</p>
    </div>
  );
}
