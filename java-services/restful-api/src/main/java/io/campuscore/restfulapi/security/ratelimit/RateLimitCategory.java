package io.campuscore.restfulapi.security.ratelimit;

/**
 * Defines semantic categories of mutating POST APIs and their default rate limits.
 */
public enum RateLimitCategory {
    AUTH_LOGIN(15, 60, "Đăng nhập xác thực"),
    AUTH_REGISTER(10, 60, "Đăng ký tài khoản"),
    AUTH_CHANGE_PASSWORD(5, 60, "Đổi mật khẩu"),
    AUTH_REFRESH(30, 60, "Làm mới phiên đăng nhập"),
    ENROLLMENT_MUTATION(15, 60, "Đăng ký học phần"),
    THESIS_MUTATION(20, 60, "Đề tài đồ án tốt nghiệp"),
    GRADING_MUTATION(20, 60, "Quản lý điểm số"),
    ANNOUNCEMENT_MUTATION(20, 60, "Thông báo & Trợ lý ảo"),
    ADMIN_MUTATION(30, 60, "Quản trị hệ thống"),
    NOTIFICATION_MUTATION(30, 60, "Thông báo cá nhân"),
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
