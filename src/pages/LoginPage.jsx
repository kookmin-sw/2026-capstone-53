import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './AuthPage.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');

  const handleLogin = () => {
    if (!id || !pw) return;
    localStorage.setItem('isLoggedIn', 'true');
    navigate('/');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleLogin();
  };

  return (
    <div className="auth">
      <div className="auth__glow" />

      <div className="auth__card">
        {/* 로고 */}
        <p className="auth__app-name">오늘어디</p>
        <p className="auth__tagline">매일 함께하는 경로 알리미, 「오늘어디」</p>

        {/* 입력 */}
        <div className="auth__fields">
          <input
            className="auth__input"
            type="text"
            placeholder="아이디"
            value={id}
            onChange={e => setId(e.target.value)}
            onKeyDown={handleKeyDown}
            autoComplete="username"
          />
          <input
            className="auth__input"
            type="password"
            placeholder="비밀번호"
            value={pw}
            onChange={e => setPw(e.target.value)}
            onKeyDown={handleKeyDown}
            autoComplete="current-password"
          />
        </div>

        {/* 로그인 버튼 */}
        <button className="auth__btn" onClick={handleLogin}>
          로그인
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
