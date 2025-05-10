package org.springframework.db;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class DbHelper {

    private final VectorStore vectorStore;

    @Deprecated
    @Transactional
    public void add(List<Document> documents) {
        vectorStore.add(documents);
    }

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
