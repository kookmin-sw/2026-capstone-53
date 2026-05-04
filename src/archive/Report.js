import React from 'react';
import './EmptyPage.css';

function Report() {
  return (
    <div className="empty-page">
      <div className="empty-page__header">
        <h1 className="empty-page__title">리포트</h1>
      </div>
      <div className="empty-page__body">
        <div className="empty-page__icon">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none">
            <path
              d="M9 19V13M12 19V9M15 19V15M5 3H19C20.1046 3 21 3.89543 21 5V19C21 20.1046 20.1046 21 19 21H5C3.89543 21 3 20.1046 3 19V5C3 3.89543 3.89543 3 5 3Z"
              stroke="#D1D5DB"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
        <p className="empty-page__label">리포트</p>
        <p className="empty-page__desc">이용 통계 및 절약 리포트가 곧 추가될 예정입니다</p>
      </div>
    </div>
  );
}

export default Report;
