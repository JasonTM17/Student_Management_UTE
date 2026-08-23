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

@RestController
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1")
public class AdminCatalogMutationController {

    private final AdminCatalogMutationService catalog;

    public AdminCatalogMutationController(AdminCatalogMutationService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/departments")
    public Map<String, Object> createDepartment(@RequestBody Map<String, Object> input) { return catalog.createDepartment(input); }

    @PostMapping("/academic-years")
    public Map<String, Object> createAcademicYear(@RequestBody Map<String, Object> input) { return catalog.createAcademicYear(input); }

    @PostMapping("/courses")
    public Map<String, Object> createCourse(@RequestBody Map<String, Object> input) { return catalog.createCourse(input); }

    @PostMapping("/classrooms")
    public Map<String, Object> createClassroom(@RequestBody Map<String, Object> input) { return catalog.createClassroom(input); }

    @PostMapping("/semesters")
    public Map<String, Object> createSemester(@RequestBody Map<String, Object> input) { return catalog.createSemester(input); }

    @PostMapping("/sections")
    public Map<String, Object> createSection(@RequestBody Map<String, Object> input) { return catalog.createSection(input); }

    @PutMapping("/departments/{id}")
    public Map<String, Object> updateDepartment(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Department\"", id, input); }

    @PutMapping("/academic-years/{id}")
    public Map<String, Object> updateAcademicYear(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"AcademicYear\"", id, input); }

    @PutMapping("/courses/{id}")
    public Map<String, Object> updateCourse(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Course\"", id, input); }

    @PutMapping("/classrooms/{id}")
    public Map<String, Object> updateClassroom(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Classroom\"", id, input); }

    @PutMapping("/semesters/{id}")
    public Map<String, Object> updateSemester(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Semester\"", id, input); }

    @PutMapping("/sections/{id}")
    public Map<String, Object> updateSection(@PathVariable String id, @RequestBody Map<String, Object> input) { return catalog.update("\"academic\".\"Section\"", id, input); }

    @DeleteMapping("/departments/{id}")
    public Map<String, String> deleteDepartment(@PathVariable String id) { return delete("\"academic\".\"Department\"", id); }

    @DeleteMapping("/academic-years/{id}")
    public Map<String, String> deleteAcademicYear(@PathVariable String id) { return delete("\"academic\".\"AcademicYear\"", id); }

    @DeleteMapping("/courses/{id}")
    public Map<String, String> deleteCourse(@PathVariable String id) { return delete("\"academic\".\"Course\"", id); }

    @DeleteMapping("/classrooms/{id}")
    public Map<String, String> deleteClassroom(@PathVariable String id) { return delete("\"academic\".\"Classroom\"", id); }

    @DeleteMapping("/semesters/{id}")
    public Map<String, String> deleteSemester(@PathVariable String id) { return delete("\"academic\".\"Semester\"", id); }

    @DeleteMapping("/sections/{id}")
    public Map<String, String> deleteSection(@PathVariable String id) { return delete("\"academic\".\"Section\"", id); }

    private Map<String, String> delete(String table, String id) {
        catalog.delete(table, id);
        return Map.of("message", "Resource deleted successfully");
    }
}
