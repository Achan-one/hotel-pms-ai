package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.hotel.service.DuplicateResourceException;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 컨트롤러에서 잡지 못한 예외를 ApiResponse 형태로 바꿔 내려준다.
 * 컨트롤러 안의 try/catch가 먼저 처리하므로 기존 응답은 그대로다.
 * 표준 MVC 예외(잘못된 JSON, 지원하지 않는 메서드 등)는 상위 클래스가 원래 상태 코드로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateResourceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("다른 직원이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> invalidDate(DateTimeParseException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("날짜 형식이 올바르지 않습니다: " + e.getParsedString()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    // 여기까지 온 건 예상하지 못한 오류다. 내부 메시지는 로그에만 남기고 응답에는 싣지 않는다.
    // 보안 예외는 Spring Security의 필터가 401, 403으로 처리하도록 그대로 다시 던진다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("서버 내부 오류가 발생했습니다."));
    }

    // @Valid 검증 실패는 어느 필드가 왜 틀렸는지 알려준다.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(status).body(ApiResponse.fail(message));
    }

    // 나머지 표준 MVC 예외도 본문만 ApiResponse로 맞춘다. 파서 내부 메시지는 내보내지 않는다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e,
                                                              Object body,
                                                              HttpHeaders headers,
                                                              HttpStatusCode status,
                                                              WebRequest request) {
        String message = status.is4xxClientError()
                ? "요청을 처리할 수 없습니다. 요청 형식을 확인해 주세요."
                : "서버 내부 오류가 발생했습니다.";
        return ResponseEntity.status(status).headers(headers).body(ApiResponse.fail(message));
    }
}
