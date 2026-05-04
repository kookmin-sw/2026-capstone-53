import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './AuthPage.css';

export default function SignupPage() {
  const navigate = useNavigate();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [pwConfirm, setPwConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [touched, setTouched] = useState({});
  const [toast, setToast] = useState(false);

  const idError     = touched.id       && id.length > 0 && id.length < 4;
  const pwError     = touched.pwConfirm && pwConfirm.length > 0 && pw !== pwConfirm;

  const isValid =
    id.length >= 4 &&
    pw.length >= 8 &&
    pw === pwConfirm &&
    nickname.length > 0;

  const handleSignup = () => {
    if (!isValid) return;
    setToast(true);
    setTimeout(() => {
      setToast(false);
      navigate('/login');
    }, 1500);
  };

  const blur = (field) => setTouched(prev => ({ ...prev, [field]: true }));

  return (
    <div className="auth">
      <div className="auth__inner">

        {/* 상단 뒤로가기 + 타이틀 */}
        <div className="auth__topbar">
          <button className="auth__back-btn" onClick={() => navigate('/login')}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2.2"
                strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            뒤로
          </button>
          <h1 className="auth__page-title">회원가입</h1>
        </div>

        {/* 아이디 */}
        <div className="auth__field">
          <input
            className={`auth__input${idError ? ' auth__input--error' : ''}`}
            type="text"
            placeholder="아이디 (4자 이상)"
            value={id}
            onChange={e => setId(e.target.value)}
            onBlur={() => blur('id')}
            autoComplete="username"
          />
          {idError && <span className="auth__error">아이디는 4자 이상이어야 해요</span>}
        </div>

        {/* 비밀번호 */}
        <div className="auth__field">
          <input
            className="auth__input"
            type="password"
            placeholder="비밀번호 (8자 이상)"
            value={pw}
            onChange={e => setPw(e.target.value)}
            onBlur={() => blur('pw')}
            autoComplete="new-password"
          />
        </div>

        {/* 비밀번호 확인 */}
        <div className="auth__field">
          <input
            className={`auth__input${pwError ? ' auth__input--error' : ''}`}
            type="password"
            placeholder="비밀번호 확인"
            value={pwConfirm}
            onChange={e => setPwConfirm(e.target.value)}
            onBlur={() => blur('pwConfirm')}
            autoComplete="new-password"
          />
          {pwError && <span className="auth__error">비밀번호가 일치하지 않아요</span>}
        </div>

        {/* 닉네임 */}
        <div className="auth__field">
          <input
            className="auth__input"
            type="text"
            placeholder="닉네임"
            value={nickname}
            onChange={e => setNickname(e.target.value)}
            autoComplete="nickname"
          />
        </div>

        {/* 가입 버튼 */}
        <button
          className={`auth__btn auth__btn-signup${!isValid ? ' auth__btn--disabled' : ''}`}
          onClick={handleSignup}
          disabled={!isValid}
        >
          가입하기
        </button>

        {/* 로그인 링크 */}
        <p className="auth__footer">
          이미 계정이 있으신가요?{' '}
          <button className="auth__link" onClick={() => navigate('/login')}>
            로그인
          </button>
        </p>

      </div>

      {toast && <div className="auth__toast">가입 완료!</div>}
    </div>
  );
}
