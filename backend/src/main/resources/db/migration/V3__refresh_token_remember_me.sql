-- PLT-01: refresh token nhớ lựa chọn "Ghi nhớ đăng nhập" để giữ đúng kiểu cookie khi xoay vòng token
--   remember_me = TRUE  -> cookie bền 7 ngày
--   remember_me = FALSE -> cookie phiên, mất khi đóng trình duyệt
ALTER TABLE refresh_tokens
    ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
