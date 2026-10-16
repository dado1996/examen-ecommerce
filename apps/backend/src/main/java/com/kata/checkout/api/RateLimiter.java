package com.kata.checkout.api;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Límite de permisos por clave (la IP del cliente) con GCRA, una variante del token bucket que
 * guarda un solo instante por clave: el "tiempo teórico de llegada" de la siguiente petición.
 *
 * <p>Con {@code requests} permisos por {@code window}, se recupera uno cada {@code window / requests}
 * y se admiten hasta {@code requests} seguidos. Vive en memoria: cada instancia del backend lleva su
 * propia cuenta. Para no crecer sin límite, cuando hay {@code maxKeys} claves se olvidan las que ya
 * recuperaron todos sus permisos.
 */
final class RateLimiter {

    private final long intervalNanos;
    private final long windowNanos;
    private final int maxKeys;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, Long> theoreticalArrival = new ConcurrentHashMap<>();

    RateLimiter(int requests, Duration window, int maxKeys, LongSupplier nanoTime) {
        if (requests < 1 || !window.isPositive()) {
            throw new IllegalArgumentException("El límite necesita al menos un permiso y una ventana positiva");
        }
        this.windowNanos = window.toNanos();
        this.intervalNanos = windowNanos / requests;
        this.maxKeys = maxKeys;
        this.nanoTime = nanoTime;
    }

    /** Consume un permiso si hay uno libre. Devuelve cero si se concedió, o cuánto esperar si no. */
    Duration tryAcquire(String key) {
        long now = now();
        long[] wait = {0};
        theoreticalArrival.compute(key, (k, tat) -> {
            long next = Math.max(tat == null ? now : tat, now) + intervalNanos;
            if (next - windowNanos > now) {
                wait[0] = next - windowNanos - now;
                return tat;
            }
            return next;
        });
        return Duration.ofNanos(wait[0]);
    }

    /** Consume un permiso aunque no quede ninguno libre: sirve para contar fallos. */
    void record(String key) {
        long now = now();
        theoreticalArrival.merge(key, now + intervalNanos, (tat, ignored) -> Math.max(tat, now) + intervalNanos);
    }

    /** Cuánto falta para que haya un permiso libre, sin consumirlo. */
    Duration waitTime(String key) {
        Long tat = theoreticalArrival.get(key);
        long now = nanoTime.getAsLong();
        long wait = tat == null ? 0 : Math.max(tat, now) + intervalNanos - windowNanos - now;
        return wait > 0 ? Duration.ofNanos(wait) : Duration.ZERO;
    }

    int trackedKeys() {
        return theoreticalArrival.size();
    }

    private long now() {
        long now = nanoTime.getAsLong();
        if (theoreticalArrival.size() >= maxKeys) {
            theoreticalArrival.values().removeIf(tat -> tat <= now);
        }
        return now;
    }
}
