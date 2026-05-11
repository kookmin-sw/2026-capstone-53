import React from 'react';
import './ScheduleCard.css';

function ScheduleCard({ schedule, departureTime, departureMinutes, bufferMinutes, weather }) {
  return (
    <div className="schedule-card">
      <div className="schedule-card__left">
        <div className="schedule-card__badge">다음 일정</div>
        <div className="schedule-card__title">{schedule.title}</div>

        {/* 출발 → 도착 시간 */}
        <div className="schedule-card__timerow">
          <span className="schedule-card__depart">{departureTime}</span>
          <svg width="16" height="10" viewBox="0 0 20 10" fill="none" className="schedule-card__timearrow">
            <path d="M1 5H17M13 1l4 4-4 4" stroke="rgba(255,255,255,0.5)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="schedule-card__arrive">{schedule.arrivalTime}</span>
        </div>

        {/* 출발지 → 목적지 */}
        <div className="schedule-card__route">
          <span className="schedule-card__origin">{schedule.originName.split(' ')[0]}</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" className="schedule-card__arrow">
            <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="rgba(255,255,255,0.5)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="schedule-card__dest">{schedule.destinationName}</span>
        </div>
      </div>

      <div className="schedule-card__right">
        <div className="schedule-card__countdown">
          <span className="schedule-card__countdown-num">{departureMinutes}</span>
          <span className="schedule-card__countdown-unit">후 출발</span>
        </div>
      </div>
    </div>
  );
}

export default ScheduleCard;
