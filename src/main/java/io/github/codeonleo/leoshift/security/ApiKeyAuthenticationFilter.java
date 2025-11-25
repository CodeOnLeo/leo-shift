package io.github.codeonleo.leoshift.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Value("${app.api-key}")
    private String apiKey;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SCHEDULED_REMINDER_PATH = "/api/push/send-scheduled-reminder";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 스케줄된 리마인더 엔드포인트만 API 키 검증
        if (SCHEDULED_REMINDER_PATH.equals(requestPath)) {
            String providedApiKey = request.getHeader(API_KEY_HEADER);

            if (!StringUtils.hasText(providedApiKey) || !apiKey.equals(providedApiKey)) {
                log.warn("유효하지 않은 API 키로 접근 시도: {}", request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid API Key\"}");
                response.setContentType("application/json");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
