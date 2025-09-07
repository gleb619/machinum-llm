package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class CustomRequestLoggingFilter extends CommonsRequestLoggingFilter {

    private final String afterMessagePrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            super.doFilterInternal(request, response, filterChain);
        } finally {
            afterRequest(request, response.getStatus());
        }
    }

    private void afterRequest(HttpServletRequest request, int status) {
        String message = createMessage(request, afterMessagePrefix, "");
        logger.debug("%s, status=%s".formatted(message, status));
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        // ignore
    }

}