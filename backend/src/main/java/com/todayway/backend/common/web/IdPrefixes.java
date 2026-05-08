package com.todayway.backend.common.web;

/**
 * 명세 §1.7 — 외부 노출 ID 의 도메인별 prefix 상수 단일 출처.
 *
 * <p>{@code mem_} / {@code sch_} / {@code sub_} 와 같은 prefix 가 controller / DTO / service
 * 여러 곳에 흩뿌려져 silent drift 가 일어나지 않도록 본 클래스로 모은다. 외부 ID 빌드는 caller 가
 * {@code IdPrefixes.MEMBER + uid} 형태로 직접 concat (모든 도메인 동일 패턴).
 */
public final class IdPrefixes {

    public static final String MEMBER = "mem_";
    public static final String SCHEDULE = "sch_";
    public static final String SUBSCRIPTION = "sub_";

    private IdPrefixes() {
    }

    /**
     * 외부 ID 가 prefix 로 시작하면 떼어내고, 아니면 입력 그대로 반환. {@code null} 안전.
     *
     * <p>strict 검증이 필요한 caller (예: PATH_VARIABLE 형식 위반 시 400) 는 본 메서드 호출 전
     * 별도 검증 로직을 둔다 — 본 메서드는 silent strip 만.
     */
    public static String strip(String externalId, String prefix) {
        if (externalId != null && externalId.startsWith(prefix)) {
            return externalId.substring(prefix.length());
        }
        return externalId;
    }
}
