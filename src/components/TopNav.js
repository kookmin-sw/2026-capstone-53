import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import logo from '../assets/brand/logo.svg';
import './TopNav.css';

const tabs = [
  { path: '/', label: '홈' },
  { path: '/calendar', label: '캘린더' },
  { path: '/settings', label: '설정' },
];

function TopNav() {
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="top-nav">
      <div className="top-nav__inner">
        {/* 로고 */}
        <button className="top-nav__logo" onClick={() => navigate('/')}>
          <img src={logo} alt="오늘어디" className="top-nav__logo-img" />
        </button>

        {/* 네비게이션 링크 */}
        <nav className="top-nav__links">
          {tabs.map((tab) => {
            const active = location.pathname === tab.path;
            return (
              <button
                key={tab.path}
                className={`top-nav__link ${active ? 'top-nav__link--active' : ''}`}
                onClick={() => navigate(tab.path)}
              >
                {tab.label}
              </button>
            );
          })}
        </nav>

        {/* 우측: 테마 토글 + 알림 버튼 */}
        <div className="top-nav__actions">
          <button className="top-nav__action-btn top-nav__theme-btn" onClick={toggleTheme} aria-label="테마 전환">
            {theme === 'dark' ? (
              /* 해 아이콘 (라이트로 전환) */
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="4" stroke="#F5C842" strokeWidth="2"/>
                <path d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M2 12h2M20 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" stroke="#F5C842" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            ) : (
              /* 달 아이콘 (다크로 전환) */
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 0 0 9.79 9.79z" stroke="#374151" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
          </button>
          <button className="top-nav__action-btn" aria-label="알림" onClick={() => navigate('/notifications')}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path
                d="M15 17H20L18.5951 15.5951C18.2141 15.2141 18 14.6973 18 14.1585V11C18 8.38757 16.3304 6.16509 14 5.34142V5C14 3.89543 13.1046 3 12 3C10.8954 3 10 3.89543 10 5V5.34142C7.66962 6.16509 6 8.38757 6 11V14.1585C6 14.6973 5.78595 15.2141 5.40493 15.5951L4 17H9M15 17V18C15 19.6569 13.6569 21 12 21C10.3431 21 9 19.6569 9 18V17M15 17H9"
                stroke="#374151"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            <span className="top-nav__notif-dot" />
          </button>
        </div>
      </div>
    </header>
  );
}

export default TopNav;
