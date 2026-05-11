import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import './BottomNav.css';

const tabs = [
  {
    path: '/',
    label: '홈',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path
          d="M3 12L5 10M5 10L12 3L19 10M5 10V20C5 20.5523 5.44772 21 6 21H9M19 10L21 12M19 10V20C19 20.5523 18.5523 21 18 21H15M9 21C9 21 9 15 12 15C15 15 15 21 15 21M9 21H15"
          stroke={active ? '#3B82F6' : '#9CA3AF'}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    path: '/calendar',
    label: '캘린더',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <rect
          x="3" y="4" width="18" height="18" rx="2"
          stroke={active ? '#3B82F6' : '#9CA3AF'}
          strokeWidth="2"
        />
        <path
          d="M3 9H21M8 2V6M16 2V6"
          stroke={active ? '#3B82F6' : '#9CA3AF'}
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="8" cy="14" r="1" fill={active ? '#3B82F6' : '#9CA3AF'} />
        <circle cx="12" cy="14" r="1" fill={active ? '#3B82F6' : '#9CA3AF'} />
        <circle cx="16" cy="14" r="1" fill={active ? '#3B82F6' : '#9CA3AF'} />
      </svg>
    ),
  },
  {
    path: '/settings',
    label: '설정',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path
          d="M10.3246 4.31731C10.751 2.5609 13.249 2.5609 13.6754 4.31731C13.9508 5.45193 15.2507 5.99038 16.2478 5.38285C17.7913 4.44239 19.5576 6.2087 18.6172 7.75218C18.0096 8.74925 18.5481 10.0492 19.6827 10.3246C21.4391 10.751 21.4391 13.249 19.6827 13.6754C18.5481 13.9508 18.0096 15.2507 18.6172 16.2478C19.5576 17.7913 17.7913 19.5576 16.2478 18.6172C15.2507 18.0096 13.9508 18.5481 13.6754 19.6827C13.249 21.4391 10.751 21.4391 10.3246 19.6827C10.0492 18.5481 8.74926 18.0096 7.75219 18.6172C6.2087 19.5576 4.44239 17.7913 5.38285 16.2478C5.99038 15.2507 5.45193 13.9508 4.31731 13.6754C2.5609 13.249 2.5609 10.751 4.31731 10.3246C5.45193 10.0492 5.99038 8.74926 5.38285 7.75218C4.44239 6.2087 6.2087 4.44239 7.75219 5.38285C8.74926 5.99038 10.0492 5.45193 10.3246 4.31731Z"
          stroke={active ? '#3B82F6' : '#9CA3AF'}
          strokeWidth="2"
        />
        <circle cx="12" cy="12" r="3" stroke={active ? '#3B82F6' : '#9CA3AF'} strokeWidth="2" />
      </svg>
    ),
  },
];

function BottomNav() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <nav className="bottom-nav">
      {tabs.map((tab) => {
        const active = location.pathname === tab.path;
        return (
          <button
            key={tab.path}
            className={`bottom-nav__tab ${active ? 'bottom-nav__tab--active' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            <span className="bottom-nav__icon">{tab.icon(active)}</span>
            <span className="bottom-nav__label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default BottomNav;
