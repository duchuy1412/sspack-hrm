package vn.sspack.hrm.platform.api.auth;

import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.sspack.hrm.shared.security.DataScope;

/** DTO của API xác thực. */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "Thông tin đăng nhập")
    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập email")
            @Email(message = "Email không đúng định dạng")
            @Size(max = 255, message = "Email tối đa 255 ký tự")
            String email,

            @NotBlank(message = "Vui lòng nhập mật khẩu")
            @Size(max = 128, message = "Mật khẩu tối đa 128 ký tự")
            String password,

            @Schema(description = "Ghi nhớ đăng nhập: cookie refresh token tồn tại 7 ngày thay vì theo phiên trình duyệt")
            boolean rememberMe) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", rememberMe=" + rememberMe + "]";
        }
    }

    @Schema(description = "Access token; refresh token được đặt trong cookie HttpOnly")
    public record AccessTokenResponse(
            String accessToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(description = "Số giây đến khi access token hết hạn", example = "900") long expiresIn) {
    }

    @Schema(description = "Người đang đăng nhập")
    public record MeResponse(Long id, String email, String preferredLocale, List<RoleResponse> roles,
                             Set<String> permissions) {
    }

    public record RoleResponse(String code, String name, DataScope dataScope) {
    }
}
