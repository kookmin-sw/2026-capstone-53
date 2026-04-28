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
}
