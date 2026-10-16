package com.kata.checkout.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra el límite del cuerpo en {@code /api/**} y el rate limiting del checkout. */
@Configuration
public class ApiLimitsConfig implements WebMvcConfigurer {

    /** IPs que se recuerdan a la vez antes de olvidar las inactivas. */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final RequestLimitsProperties limits;
    private final RateLimitInterceptor rateLimit;

    public ApiLimitsConfig(RequestLimitsProperties limits) {
        this.limits = limits;
        this.rateLimit = new RateLimitInterceptor(limiter(limits.checkoutRate()), limiter(limits.couponFailures()));
    }

    @Bean
    FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilter() {
        FilterRegistrationBean<BodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new BodySizeLimitFilter(limits.maxBodySize().toBytes()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimit)
                .addPathPatterns("/api/v1/checkouts", "/api/v1/checkouts/preview");
    }

    private static RateLimiter limiter(RequestLimitsProperties.Rate rate) {
        return new RateLimiter(rate.requests(), rate.window(), MAX_TRACKED_CLIENTS, System::nanoTime);
    }
}
