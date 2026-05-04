import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import { SettingsProvider } from './contexts/SettingsContext';
import TopNav from './components/TopNav';
import BottomNav from './components/BottomNav';
import HomeV2Route from './pages/HomeV2Route';
import CalendarPage from './pages/Calendar';
import MapPage from './pages/MapPage';
import Settings from './pages/Settings';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import NotificationPage from './pages/NotificationPage';
import './App.css';
import './styles/dark.css';

const AUTH_PATHS = ['/login', '/signup'];
const HIDE_NAV_PATHS = ['/map', '/login', '/signup', '/notifications'];

function RequireAuth({ children }) {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
  return isLoggedIn ? children : <Navigate to="/login" replace />;
}

function AppLayout() {
  const location = useLocation();
  const hideNav = HIDE_NAV_PATHS.includes(location.pathname);
  const { theme } = useTheme();

  useEffect(() => {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      meta.setAttribute('content', theme === 'dark' ? '#8BB5E0' : '#2563EB');
    }
  }, [theme]);

  return (
    <div className="app-shell">
      {!hideNav && <TopNav />}
      <main className="app-main">
        <Routes>
          {/* 인증 불필요 */}
          <Route path="/login"  element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />

          {/* 인증 필요 */}
          <Route path="/" element={<RequireAuth><HomeV2Route /></RequireAuth>} />
          <Route path="/calendar" element={<RequireAuth><CalendarPage /></RequireAuth>} />
          <Route path="/map"      element={<RequireAuth><MapPage /></RequireAuth>} />
          <Route path="/settings" element={<RequireAuth><Settings /></RequireAuth>} />
          <Route path="/notifications" element={<RequireAuth><NotificationPage /></RequireAuth>} />
        </Routes>
      </main>
      {!hideNav && <BottomNav />}
    </div>
  );
}

function App() {
  return (
    <SettingsProvider>
      <ThemeProvider>
        <BrowserRouter>
          <AppLayout />
        </BrowserRouter>
      </ThemeProvider>
    </SettingsProvider>
  );
}

export default App;
