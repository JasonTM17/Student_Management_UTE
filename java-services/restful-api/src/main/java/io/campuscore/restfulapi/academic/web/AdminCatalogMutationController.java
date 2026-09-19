package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AdminCatalogMutationService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Academic Catalog Management (Admin)", description = "Quản trị danh mục học vụ: Khoa, Năm học, Học phần, Phòng học, Học kỳ và Lớp học phần")
@RestController
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1")
public class AdminCatalogMutationController {

    private final AdminCatalogMutationService catalog;

    public AdminCatalogMutationController(AdminCatalogMutationService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Tạo Khoa mới")
    @PostMapping("/departments")
    public Map<String, Object> createDepartment(@RequestBody Map<String, Object> input) { return catalog.createDepartment(input); }

    @Operation(summary = "Tạo Năm học mới")
    @PostMapping("/academic-years")
    public Map<String, Object> createAcademicYear(@RequestBody Map<String, Object> input) { return catalog.createAcademicYear(input); }

    @Operation(summary = "Tạo Môn học / Học phần mới")
    @PostMapping("/courses")
    public Map<String, Object> createCourse(@RequestBody Map<String, Object> input) { return catalog.createCourse(input); }

    @Operation(summary = "Tạo Phòng học mới")
    @PostMapping("/classrooms")
    public Map<String, Object> createClassroom(@RequestBody Map<String, Object> input) { return catalog.createClassroom(input); }

    @Operation(summary = "Tạo Học kỳ mới")
    @PostMapping("/semesters")
    public Map<String, Object> createSemester(@RequestBody Map<String, Object> input) { return catalog.createSemester(input); }

    @Operation(summary = "Tạo Lớp học phần mới")
    @PostMapping("/sections")
    public Map<String, Object> createSection(@RequestBody Map<String, Object> input) { return catalog.createSection(input); }

    @Operation(summary = "Cập nhật Khoa")
    @PutMapping("/departments/{id}")
    public Map<String, Object> updateDepartment(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Department\"", id, input); }

    @Operation(summary = "Cập nhật Năm học")
    @PutMapping("/academic-years/{id}")
    public Map<String, Object> updateAcademicYear(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"AcademicYear\"", id, input); }

    @Operation(summary = "Cập nhật Môn học / Học phần")
    @PutMapping("/courses/{id}")
    public Map<String, Object> updateCourse(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Course\"", id, input); }

    @Operation(summary = "Cập nhật Phòng học")
    @PutMapping("/classrooms/{id}")
    public Map<String, Object> updateClassroom(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Classroom\"", id, input); }

    @Operation(summary = "Cập nhật Học kỳ")
    @PutMapping("/semesters/{id}")
    public Map<String, Object> updateSemester(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Semester\"", id, input); }

    @Operation(summary = "Cập nhật Lớp học phần")
    @PutMapping("/sections/{id}")
    public Map<String, Object> updateSection(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Section\"", id, input); }

    @Operation(summary = "Xóa Khoa")
    @DeleteMapping("/departments/{id}")
    public Map<String, String> deleteDepartment(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"Department\"", id); }

    @Operation(summary = "Xóa Năm học")
    @DeleteMapping("/academic-years/{id}")
    public Map<String, String> deleteAcademicYear(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"AcademicYear\"", id); }

    @Operation(summary = "Xóa Môn học / Học phần")
    @DeleteMapping("/courses/{id}")
    public Map<String, String> deleteCourse(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"Course\"", id); }

    @Operation(summary = "Xóa Phòng học")
    @DeleteMapping("/classrooms/{id}")
    public Map<String, String> deleteClassroom(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"Classroom\"", id); }

    @Operation(summary = "Xóa Học kỳ")
    @DeleteMapping("/semesters/{id}")
    public Map<String, String> deleteSemester(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"Semester\"", id); }

    @Operation(summary = "Xóa Lớp học phần")
    @DeleteMapping("/sections/{id}")
    public Map<String, String> deleteSection(@Parameter(description = "Mã định danh (UUID)", required = true) @PathVariable String id) { return delete("\"academic\".\"Section\"", id); }

    private Map<String, String> delete(String table, String id) {
        catalog.delete(table, id);
        return Map.of("message", "Resource deleted successfully");
    }
}
