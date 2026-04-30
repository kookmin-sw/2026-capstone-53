package com.todayway.backend.route;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RouteService Bean 등록.
 * 이상진이 Step 6에서 OdsayRouteService를 @Component / @Service로 등록하면
 * @ConditionalOnMissingBean이 NoOpRouteService 미생성 → 자연 비활성 (chicken-and-egg 우회).
 */
@Configuration
public class RouteServiceConfig {

    @Bean
    @ConditionalOnMissingBean(RouteService.class)
    public RouteService noOpRouteService() {
        return new NoOpRouteService();
    }
}
