import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockNotifications } from '../data/mockData';
import './NotificationPage.css';

/* 날짜 레이블 포매터: "오늘", "어제", "5월 2일 금요일" */
function dateLabel(isoString) {
  const d = new Date(isoString);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);

  const same = (a, b) =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate();

  if (same(d, today)) return '오늘';
  if (same(d, yesterday)) return '어제';
  return d.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' });
}

/* ISO → "YYYY-MM-DD" 키 */
function dayKey(isoString) {
  return isoString.substring(0, 10);
}

/* 알림 아이템 */
function NotifItem({ item, onRead }) {
  return (
    <div
      className={`ni ${item.read ? '' : 'ni--unread'}`}
      onClick={() => !item.read && onRead(item.id)}
    >
      <div className={`ni__dot-wrap`}>
        <div className="ni__icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path
              d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            />
          </svg>
        </div>
        {!item.read && <span className="ni__unread-dot" />}
      </div>

      <div className="ni__body">
        <div className="ni__row1">
          <span className="ni__title">{item.scheduleTitle}</span>
          <span className="ni__time-badge">
            {item.departureTime} 출발
          </span>
        </div>
        <div className="ni__row2">
          <span className="ni__dest">{item.destination} 도착 {item.arrivalTime}</span>
        </div>
      </div>
    </div>
  );
}

export default function NotificationPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState(mockNotifications);

  const markRead = (id) =>
    setItems(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));

  const markAllRead = () =>
    setItems(prev => prev.map(n => ({ ...n, read: true })));

  const unreadCount = items.filter(n => !n.read).length;

  /* 날짜별 그룹핑 */
  const groups = items.reduce((acc, item) => {
    const key = dayKey(item.createdAt);
    if (!acc[key]) acc[key] = { label: dateLabel(item.createdAt), items: [] };
    acc[key].items.push(item);
    return acc;
  }, {});

  const groupKeys = Object.keys(groups).sort((a, b) => b.localeCompare(a));

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
        {unreadCount > 0 && (
          <button className="notif__mark-all" onClick={markAllRead}>
            모두 읽음
          </button>
        )}
      </div>

      {/* 내용 */}
      {items.length === 0 ? (
        <div className="notif__empty">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" className="notif__empty-icon">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"
              stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <p className="notif__empty-title">아직 알림이 없어요</p>
          <p className="notif__empty-desc">일정을 등록하면 출발 시간에 맞춰 알려드려요</p>
        </div>
      ) : (
        <div className="notif__list">
          {groupKeys.map(key => (
            <div key={key} className="notif__group">
              <div className="notif__group-label">{groups[key].label}</div>
              {groups[key].items.map(item => (
                <NotifItem key={item.id} item={item} onRead={markRead} />
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
