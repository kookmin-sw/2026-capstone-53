package com.todayway.backend.push.sender;

import com.todayway.backend.push.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * Web Push 전송 어댑터. {@code nl.martijndwars:web-push:5.1.x} (동기 {@link PushService}) 래퍼.
 *
 * <p>책임:
 * <ul>
 *   <li>BouncyCastle Provider 1회 등록 (클래스 로드 시 static init — Spring lifecycle 이전)</li>
 *   <li>{@link VapidProperties} 기반 {@code PushService} lazy 초기화 (publicKey/privateKey 미설정 시 {@link IllegalStateException})</li>
 *   <li>HTTP 응답 코드 → {@link PushSendResult} 매핑 (200/201/202 = SENT, 410 = EXPIRED, 그 외 + 예외 = FAILED)</li>
 *   <li>checked exception 다수 래핑 (라이브러리가 IOException/GeneralSecurityException/JoseException/InterruptedException 등 던짐)</li>
 * </ul>
 *
 * <p>410 Gone 처리는 호출자({@link com.todayway.backend.push.service.PushScheduler}) 책임 —
 * 결과의 {@link PushSendResult#isExpired()} 보고 {@link PushSubscription#revoke()} 호출.
 * 본 클래스는 IO만 담당하고 도메인 상태 변경 X.
 */
@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final VapidProperties vapidProperties;
    private volatile PushService pushService;

    public PushSender(VapidProperties vapidProperties) {
        this.vapidProperties = vapidProperties;
    }

    /**
     * 동기 발송. 내부에서 모든 라이브러리 예외를 잡아 {@link PushSendResult#failed(Integer)} 로 변환.
     * 호출자는 예외 catch 불필요 (단, {@link IllegalStateException} 은 키 미설정 시 propagation —
     * 운영 단계에서 즉시 발견되어야 할 설정 누락이라 graceful 처리 X).
     */
    public PushSendResult send(PushSubscription subscription, String payloadJson) {
        PushService ps = getOrCreatePushService();
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payloadJson
            );
            HttpResponse response = ps.send(notification);
            int code = response.getStatusLine().getStatusCode();

            if (code == 410) {
                log.info("Push 410 Gone — endpoint expired: subscriptionUid={}", subscription.getSubscriptionUid());
                return PushSendResult.expired();
            }
            if (code >= 200 && code < 300) {
                return PushSendResult.sent(code);
            }
            log.warn("Push HTTP {} — endpoint={}, subscriptionUid={}",
                    code, subscription.getEndpoint(), subscription.getSubscriptionUid());
            return PushSendResult.failed(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Push send interrupted: subscriptionUid={}", subscription.getSubscriptionUid());
            return PushSendResult.failed(null);
        } catch (Exception e) {
            // 보안: cause는 endpoint/payload 일부 포함 가능 → 메시지에 클래스명만 남김 (KakaoLocalClient 패턴 미러).
            log.warn("Push send failed: subscriptionUid={}, cause={}",
                    subscription.getSubscriptionUid(), e.getClass().getSimpleName());
            return PushSendResult.failed(null);
        }
    }

    private PushService getOrCreatePushService() {
        PushService cached = this.pushService;
        if (cached != null) {
            return cached;
        }
        if (!vapidProperties.isConfigured()) {
            throw new IllegalStateException(
                    "VAPID keys 미설정 — vapid.public-key / vapid.private-key 환경변수를 확인하세요.");
        }
        synchronized (this) {
            if (this.pushService == null) {
                try {
                    this.pushService = new PushService(
                            vapidProperties.getPublicKey(),
                            vapidProperties.getPrivateKey(),
                            vapidProperties.getSubject()
                    );
                } catch (GeneralSecurityException e) {
                    throw new IllegalStateException("VAPID PushService 초기화 실패: " + e.getClass().getSimpleName(), e);
                }
            }
            return this.pushService;
        }
    }
}
