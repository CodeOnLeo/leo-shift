package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * API 오류 응답.
 *
 * <p>이전 구현은 처리되지 않은 모든 500에서 원본 예외 메시지를 그대로 내보냈다.
 * Hibernate/JDBC 문구, 제약 조건 이름, 테이블·컬럼명이 클라이언트로 나갔다.
 * 그리고 모든 비즈니스 오류가 400이라 권한 오류와 입력 오류를 구분할 수 없었다.
 *
 * <p>여기서는 알려진 오류만 메시지를 내보내고, 나머지는 서버 로그에만 남긴다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    @ExceptionHandler(CurrentUser.NotAuthenticatedException.class)
    ResponseEntity<Map<String, String>> unauthenticated(CurrentUser.NotAuthenticatedException e) {
        return body(HttpStatus.UNAUTHORIZED, "unauthenticated", e.getMessage());
    }

    @ExceptionHandler(CalendarAccessService.AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(CalendarAccessService.AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "access_denied", e.getMessage());
    }

    @ExceptionHandler(CalendarAccessService.NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(CalendarAccessService.NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }

    /** 다른 사람이 같은 대상을 먼저 고쳤다. 화면에서 다시 불러오게 한다. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> conflict(ObjectOptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "conflict", "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도해 주세요");
    }

    /**
     * 없는 경로. 아래 catch-all보다 먼저 잡아야 한다.
     * 그러지 않으면 404가 500으로 보고돼 진짜 장애와 구분되지 않는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> noResource(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "not_found", "없는 경로입니다");
    }

    /** 파라미터가 없거나 형식이 틀렸다. 클라이언트 잘못이므로 400이다. */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<Map<String, String>> badParameter(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "invalid_request", "요청 형식이 올바르지 않습니다");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, String>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "지원하지 않는 방식입니다");
    }

    /** 예상하지 못한 오류. 내부 정보를 밖으로 내보내지 않는다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "요청을 처리하지 못했습니다");
    }
}
