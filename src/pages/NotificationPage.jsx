import React from 'react';
import { useNavigate } from 'react-router-dom';
import './NotificationPage.css';

export default function NotificationPage() {
  const navigate = useNavigate();

  return (
    <div className="notif">
      {/* 상단 바 */}
      <div className="notif__topbar">
        <button className="notif__back" onClick={() => navigate('/')} aria-label="뒤로">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <span className="notif__title">알림</span>
      </div>

      {/* 빈 상태 */}
      <div className="notif__empty">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" className="notif__empty-icon">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"
            stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        <p className="notif__empty-title">아직 알림이 없어요</p>
        <p className="notif__empty-desc">일정을 등록하면 출발 시간에 맞춰 알려드려요</p>
      </div>
    </div>
  );
}
