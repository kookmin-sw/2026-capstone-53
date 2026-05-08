package com.todayway.backend.external;

import com.todayway.backend.external.kakao.KakaoLocalProperties;
import com.todayway.backend.external.odsay.OdsayProperties;
import com.todayway.backend.external.tmap.TmapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        OdsayProperties.class,
        KakaoLocalProperties.class,
        TmapProperties.class,
})
public class ExternalApiConfig {
}
