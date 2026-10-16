package com.kata.checkout.application;

import java.util.function.Supplier;

/**
 * Ejecuta un trabajo en una sola transacción: si lanza una excepción, nada de lo que escribió en
 * la base queda guardado. Lo implementa la infraestructura, para que el caso de uso no dependa de Spring.
 */
public interface UnitOfWork {

    <T> T run(Supplier<T> work);
}
