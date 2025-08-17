package org.springframework.db;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class DbHelper {

    private final ApplicationContext context;


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void noTransaction(Runnable runnable) {
        runnable.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doInNewTransaction(Runnable runnable) {
        doInNewTransaction(() -> {
            runnable.run();

            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doInNewTransaction(Consumer<ApplicationContext> consumer) {
        doInNewTransaction(() -> {
            consumer.accept(context);

            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T doInNewTransaction(Supplier<T> supplier) {
        return supplier.get();
    }

    @Transactional(readOnly = true)
    public void readOnly(Runnable runnable) {
        readOnly(() -> {
            runnable.run();

            return null;
        });
    }

    @Transactional(readOnly = true)
    public <T> T readOnly(Supplier<T> supplier) {
        return supplier.get();
    }

}
