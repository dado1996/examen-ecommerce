package com.kata.checkout.infrastructure.persistence;

import com.kata.checkout.application.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacción de Spring sobre la conexión a SQLite. Como el pool tiene una sola conexión, la
 * transacción también serializa los checkouts: dos peticiones con la misma clave de idempotencia
 * no pueden buscarla a la vez y crear dos órdenes.
 */
@Component
public class TransactionTemplateUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactions;

    public TransactionTemplateUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T run(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
