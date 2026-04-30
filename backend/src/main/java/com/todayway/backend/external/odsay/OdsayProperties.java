package com.todayway.backend.external.odsay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "odsay")
public class OdsayProperties {
    private String apiKey;
    private String baseUrl;
    private int timeoutSeconds = 5;
    /** 명세 §6.1 — schedule.route_summary_json 캐시 TTL (분). 권장 10분. */
    private int cacheTtlMinutes = 10;
}
