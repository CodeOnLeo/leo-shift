package io.github.codeonleo.leoshift.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String path = request.getRequestURI();
            if (path.startsWith("/api/")) {
                log.info("API {} {} {}ms", request.getMethod(), path, elapsed);
            } else if (elapsed > 500) {
                log.info("REQ {} {} {}ms", request.getMethod(), path, elapsed);
            }
        }
    }
}
