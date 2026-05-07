import { useState, useCallback, useEffect } from 'react';
import { subscribePush } from '../api/client';

const VAPID_KEY = process.env.REACT_APP_VAPID_PUBLIC_KEY;

/** Base64url → Uint8Array (VAPID applicationServerKey 변환) */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  return Uint8Array.from([...rawData].map(c => c.charCodeAt(0)));
}

async function registerPushSubscription() {
  if (!('serviceWorker' in navigator)) return;
  if (!VAPID_KEY) {
    console.info('[push] REACT_APP_VAPID_PUBLIC_KEY 미설정 — 구독 스킵');
    return;
  }

  try {
    const reg = await navigator.serviceWorker.ready;
    const existing = await reg.pushManager.getSubscription();
    if (existing) return; // 이미 구독됨

    const subscription = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(VAPID_KEY),
    });

    const json = subscription.toJSON();
    await subscribePush({
      endpoint: json.endpoint,
      keys: {
        p256dh: json.keys.p256dh,
        auth:   json.keys.auth,
      },
    });
    console.info('[push] 구독 등록 완료');
  } catch (e) {
    console.warn('[push] 구독 실패', e);
  }
}

/**
 * PWA 푸시 알림 권한 및 구독을 관리하는 훅
 *
 * @returns {{
 *   permission: NotificationPermission,
 *   requestPermission: () => Promise<void>
 * }}
 */
export function usePushNotification() {
  const [permission, setPermission] = useState(
    typeof Notification !== 'undefined' ? Notification.permission : 'default'
  );

  // permission 변경 감지 (permissionchange 이벤트는 일부 브라우저만 지원)
  useEffect(() => {
    if (!('permissions' in navigator)) return;
    navigator.permissions.query({ name: 'notifications' }).then(status => {
      const sync = () => setPermission(Notification.permission);
      status.addEventListener('change', sync);
      return () => status.removeEventListener('change', sync);
    }).catch(() => {});
  }, []);

  const requestPermission = useCallback(async () => {
    if (!('Notification' in window)) return;
    if (Notification.permission !== 'default') return;

    const result = await Notification.requestPermission();
    setPermission(result);

    if (result === 'granted') {
      await registerPushSubscription();
    }
  }, []);

  return { permission, requestPermission };
}
