package vn.sspack.hrm.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Chuyển mọi lỗi ra API thành ProblemDetail (RFC 7807) với thông báo tiếng Việt. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.status(), ex.code(), ex.getMessage());
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetails.of(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetails.of(
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại và thử lại."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Vui lòng kiểm tra lại các trường được đánh dấu.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Nội dung yêu cầu không đúng định dạng."));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetails.of(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy đường dẫn yêu cầu."));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Các lỗi Spring MVC còn lại: giữ mã HTTP, thay tiêu đề bằng tiếng Việt
        if (body instanceof ProblemDetail problem && HttpStatus.resolve(statusCode.value()) instanceof HttpStatus s) {
            problem.setTitle(ProblemDetails.titleFor(s));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
