package io.campuscore.restfulapi.security.ratelimit;

/**
 * Defines semantic categories of mutating POST APIs and their default rate limits.
 */
public enum RateLimitCategory {
    AUTH_LOGIN(15, 60, "Đăng nhập xác thực"),
    AUTH_CHANGE_PASSWORD(5, 60, "Đổi mật khẩu"),
    AUTH_REFRESH(30, 60, "Làm mới phiên đăng nhập"),
    ENROLLMENT_MUTATION(15, 60, "Đăng ký học phần"),
    THESIS_MUTATION(20, 60, "Đề tài đồ án tốt nghiệp"),
    GRADING_MUTATION(20, 60, "Quản lý điểm số"),
    ANNOUNCEMENT_MUTATION(20, 60, "Thông báo"),
    ASSISTANT_CHAT(20, 60, "Hội thoại trợ lý ảo"),
    ADMIN_MUTATION(30, 60, "Quản trị hệ thống"),
    NOTIFICATION_MUTATION(30, 60, "Thông báo cá nhân"),
    /** Audit S6: outbound mail is expensive; 5 sends per hour per identity. */
    MAIL_MUTATION(5, 3600, "Gửi thông báo qua mail"),
    /** Audit S6: password-reset issuance; 3 resets per hour per identity. */
    PASSWORD_RESET(3, 3600, "Đặt lại mật khẩu người dùng"),
    /** Two-factor OTP verification; 10 tries per 15 minutes per IP. */
    TWO_FACTOR_VERIFY(10, 900, "Xác thực OTP hai yếu tố"),
    DEFAULT_POST(40, 60, "Thao tác chung");

    private final int defaultLimit;
    private final int defaultWindowSeconds;
    private final String description;

    RateLimitCategory(int defaultLimit, int defaultWindowSeconds, String description) {
        this.defaultLimit = defaultLimit;
        this.defaultWindowSeconds = defaultWindowSeconds;
        this.description = description;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public String getDescription() {
        return description;
    }
}
