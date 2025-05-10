package org.springframework.async;

import java.util.concurrent.CompletableFuture;

public class AsyncHelper {

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable);
    }

}
