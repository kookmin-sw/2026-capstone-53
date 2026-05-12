import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import * as serviceWorkerRegistration from './serviceWorkerRegistration';
import reportWebVitals from './reportWebVitals';

// ── MSW 조건부 시작 ──
async function bootstrap() {
  if (process.env.REACT_APP_USE_MOCK === 'true') {
    const { worker } = await import('./mocks/browser');
    const scenario = localStorage.getItem('msw-scenario') || 'default';
    await worker.start({
      onUnhandledRequest: 'bypass',
    });
    console.log(`[MSW] enabled, scenario: ${scenario}`);
  }

  const root = ReactDOM.createRoot(document.getElementById('root'));
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}

bootstrap();

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://cra.link/PWA
serviceWorkerRegistration.unregister();

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();

// 임시 디버깅용, 통합 완료 후 제거
if (process.env.NODE_ENV === 'development') {
  import('./api').then(m => { window.api = m.api; });
}
