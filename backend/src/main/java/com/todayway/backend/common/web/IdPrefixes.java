package com.todayway.backend.common.web;

/**
 * 명세 §1.7 — 외부 노출 ID 의 도메인별 prefix 상수 단일 출처.
 *
 * <p>{@code sub_} / {@code sch_} 와 같은 prefix 가 controller / DTO / service 여러 곳에 흩뿌려져
 * silent drift 가 일어나지 않도록 본 클래스로 모은다. (Step 6 PR #11 follow-up "stripPrefix DRY"
 * 트리거 수렴 — Step 7 진입으로 sub_ 추가되어 두 도메인이 동일 패턴을 갖게 됨.)
 */
public final class IdPrefixes {

    public static final String SCHEDULE = "sch_";
    public static final String SUBSCRIPTION = "sub_";

    private IdPrefixes() {
    }

    /**
     * 외부 ID 가 prefix 로 시작하면 떼어내고, 아니면 입력 그대로 반환. {@code null} 안전.
     */
    public static String strip(String externalId, String prefix) {
        if (externalId != null && externalId.startsWith(prefix)) {
            return externalId.substring(prefix.length());
        }
        return externalId;
    }
}
