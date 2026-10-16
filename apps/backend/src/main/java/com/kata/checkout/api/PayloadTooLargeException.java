package com.kata.checkout.api;

import java.io.IOException;

/**
 * El cuerpo supera el máximo permitido. Es una {@link IOException} porque surge al leer el cuerpo:
 * Spring la entrega envuelta en una {@code HttpMessageNotReadableException}.
 */
class PayloadTooLargeException extends IOException {

    PayloadTooLargeException(long maxBytes) {
        super("El cuerpo supera " + maxBytes + " bytes");
    }
}
