package com.todayway.backend.external.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kakao-local")
public class KakaoLocalProperties {
    private String apiKey;
    private String baseUrl;
    private int timeoutSeconds = 5;
}
