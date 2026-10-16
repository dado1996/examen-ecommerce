package com.kata.checkout.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limiting por IP en {@code POST /checkouts} y {@code /checkouts/preview}, que comparten la
 * cuenta. Además, una IP que acumula cupones rechazados ({@code INVALID_COUPON} o
 * {@code COUPON_EXPIRED}) queda bloqueada un tiempo en ambos endpoints, también con cupones válidos:
 * así no se pueden adivinar códigos probando muchos seguidos.
 *
 * <p>Solo cuenta los {@code POST}: el preflight CORS ({@code OPTIONS}) también pasa por los
 * interceptores y no debe consumir permisos. La IP es {@code getRemoteAddr()}; detrás de un proxy
 * hay que activar {@code server.forward-headers-strategy} para que sea la del cliente.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    /** Atributo que {@code ApiExceptionHandler} marca cuando rechaza un cupón. */
    static final String COUPON_REJECTED = RateLimitInterceptor.class.getName() + ".couponRejected";

    private final RateLimiter requests;
    private final RateLimiter couponFailures;

    RateLimitInterceptor(RateLimiter requests, RateLimiter couponFailures) {
        this.requests = requests;
        this.couponFailures = couponFailures;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String client = request.getRemoteAddr();
        reject(couponFailures.waitTime(client));
        reject(requests.tryAcquire(client));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (request.getAttribute(COUPON_REJECTED) != null) {
            couponFailures.record(request.getRemoteAddr());
        }
    }

    private static void reject(Duration wait) {
        if (wait.isPositive()) {
            throw new RateLimitExceededException(wait);
        }
    }
}
