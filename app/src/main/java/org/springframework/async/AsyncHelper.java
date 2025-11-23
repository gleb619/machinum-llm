package org.springframework.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.db.DbHelper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    public <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public <U> CompletableFuture<U> inNewTransaction(Supplier<U> supplier, Executor executor) {
        return supplyAsync(() -> dbHelper.doInNewTransaction(supplier), executor)
                .whenComplete((u, ex) -> {
                    if (Objects.nonNull(ex)) {
                        log.error("ERROR in async transaction: ", ex);
                    }
                });
    }

    public <U> List<U> executeAllInNewTransactions(List<Supplier<U>> suppliers, int maxConcurrency) {
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        try {
            List<CompletableFuture<U>> futures = suppliers.stream()
                    .map(s -> inNewTransaction(s, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }
    }

}
