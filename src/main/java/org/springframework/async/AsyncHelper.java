package org.springframework.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.db.DbHelper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class AsyncHelper {

    private final DbHelper dbHelper;

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable);
    }

    public <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }

    public <U> CompletableFuture<U> inNewTransaction(Supplier<U> supplier) {
        return inNewTransaction(supplier, (u, ex) -> {
            if (Objects.nonNull(ex)) {
                log.error("ERROR: ", ex);
            }
        });
    }

    public <U> CompletableFuture<U> inNewTransaction(Supplier<U> supplier, BiConsumer<? super U, ? super Throwable> handler) {
        return supplyAsync(() -> dbHelper.doInNewTransaction(supplier))
                .whenComplete(handler);
    }

}
