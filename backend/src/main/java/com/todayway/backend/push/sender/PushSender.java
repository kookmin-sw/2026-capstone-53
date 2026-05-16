package com.todayway.backend.push.sender;

import com.todayway.backend.push.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
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
 *   <li>HTTP 응답 코드 → {@link PushSendResult} 매핑 (2xx = SENT, 410 = EXPIRED, 그 외 + 예외 = FAILED)</li>
 *   <li>{@link HttpResponse} entity 명시적 consume (connection pool 반환 — leak 차단)</li>
 *   <li>checked exception 다수 래핑 (라이브러리가 IOException/GeneralSecurityException/JoseException/InterruptedException 등 던짐)</li>
 * </ul>
 *
 * <p>410 Gone 처리는 호출자({@link com.todayway.backend.push.service.PushReminderDispatcher}) 책임 —
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
        HttpResponse response = null;
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payloadJson
            );
            response = ps.send(notification);
            int code = response.getStatusLine().getStatusCode();

            if (code == 410) {
                log.info("Push 410 Gone — endpoint expired: subscriptionUid={}", subscription.getSubscriptionUid());
                return PushSendResult.expired();
            }
            if (code >= 200 && code < 300) {
                return PushSendResult.sent(code);
            }
            // v1.1.35 — endpoint full URL 은 push provider 가 발급한 per-device token (RFC 8030
            // §5 push subscription resource) 라 그 자체가 발송 권한 자격. 운영 로그에 전체를 남기면
            // 노출 시 다른 회원에게도 푸시 발송 가능한 위험. origin (scheme+host[:port]) 만 보존 —
            // 어느 provider (FCM / APNs / Mozilla autopush 등) 호출이 실패한 건지 분류 가능.
            log.warn("Push HTTP {} — endpointOrigin={}, subscriptionUid={}",
                    code, maskEndpoint(subscription.getEndpoint()), subscription.getSubscriptionUid());
            return PushSendResult.failed(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Push send interrupted: subscriptionUid={}", subscription.getSubscriptionUid());
            return PushSendResult.failed(null);
        } catch (Exception e) {
            // 보안: message 에는 클래스명만 — endpoint/payload 가 cause message 에 포함될 위험 차단
            // (KakaoLocalClient 패턴 미러). stack trace 자체엔 endpoint reference 없음이라 e 를
            // last vararg 로 logger 에 위임해 root cause 출력 — PushScheduler 와 일관.
            // subscriptionUid 는 internal UUID 라 안전.
            log.warn("Push send failed: subscriptionUid={}, cause={}",
                    subscription.getSubscriptionUid(), e.getClass().getSimpleName(), e);
            return PushSendResult.failed(null);
        } finally {
            if (response != null) {
                // connection pool 반환 — entity body 미read 시 idle pool 점유로 점진적 hang 가능.
                // nl.martijndwars 5.1.x 가 내부에서 자동 처리하지 않을 경우 대비 명시적 consume.
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
    }

    /**
     * v1.1.35 — endpoint URL 의 path 절단. push subscription resource path 가 per-device token 이라
     * 노출 자체가 자격 누출. URI 파싱 실패 시 "(invalid)" 반환 — fallback 으로 절대 원본을 노출하지
     * 않는다 (보안 우선). 정상 endpoint 는 RFC 8030 §5 정합이라 java.net.URI 파싱이 거의 항상 성공.
     */
    static String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "(none)";
        }
        try {
            java.net.URI uri = java.net.URI.create(endpoint);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return "(invalid)";
            }
            int port = uri.getPort();
            return port < 0 ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return "(invalid)";
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
