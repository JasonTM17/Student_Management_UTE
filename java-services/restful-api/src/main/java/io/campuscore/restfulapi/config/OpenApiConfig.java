package io.campuscore.restfulapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger Documentation Configuration for CampusUTE REST API.
 * Defines institutional metadata, security schemes (JWT Bearer), and server environments.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI campusCoreOpenAPI(
            @Value("${server.port:4010}") String serverPort,
            @Value("${spring.application.name:campuscore-restful-api}") String appName) {

        return new OpenAPI()
                .info(new Info()
                        .title("TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH (HCMUTE) - RESTful API")
                        .description("""
                                ### Hệ thống Quản trị Đào tạo & Khóa luận Tốt nghiệp CampusUTE
                                Tài liệu đặc tả kỹ thuật RESTful API theo tiêu chuẩn đào tạo và quy chế quản lý khóa luận của Trường Đại học Sư phạm Kỹ thuật TP.HCM.

                                #### Các phân hệ nghiệp vụ chính:
                                - **Xác thực & Định danh (Auth)**: JWT Token, đăng nhập đa vai trò (Sinh viên, Giảng viên, Phòng Đào tạo).
                                - **Quản lý Học vụ & CTĐT (Academic)**: 8 Khoa đào tạo HCMUTE, học phần, thời khóa biểu tuần, cơ chế điểm 50% Quá trình + 50% Cuối kỳ.
                                - **Đăng ký Học phần (Registration)**: Kiểm tra xung đột lịch học, sĩ số lớp, giới hạn tối đa 28 tín chỉ/học kỳ.
                                - **Điểm Rèn Luyện (Conduct - ĐRL)**: 5 tiêu chí rèn luyện chính thức theo Quyết định số 24/2020/QĐ-BGDĐT.
                                - **Khóa luận Tốt nghiệp & Đồ án (Thesis Lifecycle)**:
                                  - Đợt đăng ký (NCKH, TLCN, KLTN).
                                  - Đề tài theo Bộ môn & Giảng viên hướng dẫn (1-2 GVHD).
                                  - Nhóm sinh viên (tối đa 3 SV, 1 nhóm trưởng).
                                  - Nộp báo cáo luận văn (Chỉ nhóm trưởng nộp trước hạn GVPB).
                                  - Hội đồng chấm bảo vệ 3-5 thành viên (Chủ tịch, Thư ký, Phản biện, Ủy viên).
                                  - Quy tắc loại trừ GVHD không được chấm điểm (`SUPERVISOR_CANNOT_GRADE`).
                                  - Chủ tịch chốt điểm trung bình và công bố kết quả.
                                - **Công văn Thông báo Hành chính (Announcements)**: Soạn thảo theo thể thức văn bản hành chính Nghị định 30/2020/NĐ-CP.

                                #### Hướng dẫn xác thực:
                                1. Gọi endpoint `POST /api/v1/auth/login` để lấy `accessToken`.
                                2. Nhấn nút **Authorize** màu xanh ở góc trên bên phải Swagger UI.
                                3. Nhập token vào ô `bearerAuth` và nhấn **Authorize**.
                                """)
                        .version("2.4.0 (Enterprise Architecture)")
                        .contact(new Contact()
                                .name("Phòng Đào tạo - Trường ĐH Sư phạm Kỹ thuật TP.HCM")
                                .email("daotao@hcmute.edu.vn")
                                .url("https://hcmute.edu.vn"))
                        .license(new License()
                                .name("HCMUTE Educational License - Nghiêm cấm sao chép trái phép")
                                .url("https://hcmute.edu.vn/ban-quyen")))
                .servers(List.of(
                        new Server()
                                .url("http://127.0.0.1:" + serverPort)
                                .description("Máy chủ phát triển nội bộ (Localhost Dev Server)"),
                        new Server()
                                .url("https://campusute.io.vn")
                                .description("Máy chủ Production Trực tuyến")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JSON Web Token (JWT) được cấp khi đăng nhập")));
    }
}
