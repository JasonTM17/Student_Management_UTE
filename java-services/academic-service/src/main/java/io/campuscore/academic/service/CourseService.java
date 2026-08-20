package io.campuscore.academic.service;

import io.campuscore.academic.domain.Course;
import io.campuscore.academic.repository.CourseRepository;
import io.campuscore.academic.web.AcademicDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courses;

    public CourseService(CourseRepository courses) {
        this.courses = courses;
    }

    @Transactional
    public AcademicDtos.CourseResponse create(AcademicDtos.CreateCourseRequest request) {
        if (courses.existsByCode(request.code())) {
            throw new IllegalArgumentException("Course already exists");
        }
        Course course = new Course(request.code(), request.name(), request.credits(), request.departmentId());
        course.updateFields(request.code(), request.name(), request.nameEn(), request.nameVi(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.credits(), request.departmentId(), true);
        return toResponse(courses.save(course));
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.CourseResponse> findAll(int page, int limit) {
        return courses.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.CourseResponse findOne(UUID id) {
        return toResponse(courses.findById(id).orElseThrow(() -> new IllegalArgumentException("Course not found")));
    }

    @Transactional
    public AcademicDtos.CourseResponse update(UUID id, AcademicDtos.UpdateCourseRequest request) {
        Course existing = courses.findById(id).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        existing.updateFields(request.code(), request.name(), request.nameEn(), request.nameVi(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.credits(), request.departmentId(), request.active());
        return toResponse(courses.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        courses.deleteById(id);
    }

    private AcademicDtos.CourseResponse toResponse(Course course) {
        return new AcademicDtos.CourseResponse(
                course.getId(), course.getCode(), course.getName(), course.getNameEn(),
                course.getNameVi(), course.getDescription(), course.getDescriptionEn(),
                course.getDescriptionVi(), course.getCredits(), course.getDepartmentId(),
                course.isActive(), course.getCreatedAt(), course.getUpdatedAt());
    }
}
