package machinum.controller.core;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ControllerTrait {

    default <T> ResponseEntity<List<T>> pageResponse(Page<T> page) {
        var pageable = page.getPageable();

        // Extract pagination metadata
        int totalPages = page.getTotalPages();
        long totalElements = page.getTotalElements();
        int pageNumber;
        int pageSize;
        try {
            pageNumber = pageable.getPageNumber();
            pageSize = pageable.getPageSize();
        } catch (UnsupportedOperationException ignore) {
            pageNumber = 0;
            pageSize = 0;
        }

        // Build headers with pagination info
        var headers = new HttpHeaders();
        headers.add("X-Total-Pages", String.valueOf(totalPages));
        headers.add("X-Total-Elements", String.valueOf(totalElements));
        headers.add("X-Current-Page", String.valueOf(pageNumber));
        headers.add("X-Page-Size", String.valueOf(pageSize));

        return ResponseEntity.ok()
                .headers(headers)
                .body(page.getContent());
    }

    default <T> ResponseEntity<T> withCacheControl(T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(data);
    }

}
