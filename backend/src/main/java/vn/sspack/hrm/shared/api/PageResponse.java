package vn.sspack.hrm.shared.api;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/** Một trang kết quả. {@code page} bắt đầu từ 0, khớp tham số {@code page}/{@code size} của API. */
@Schema(description = "Một trang kết quả")
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        return new PageResponse<>(page.getContent().stream().<T>map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
