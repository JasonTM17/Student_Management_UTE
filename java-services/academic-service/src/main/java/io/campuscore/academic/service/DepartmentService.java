package io.campuscore.academic.service;

import io.campuscore.academic.domain.Department;
import io.campuscore.academic.repository.DepartmentRepository;
import io.campuscore.academic.web.AcademicDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departments;

    public DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Transactional
    public AcademicDtos.DepartmentResponse create(AcademicDtos.CreateDepartmentRequest request) {
        if (departments.existsByCode(request.code())) {
            throw new IllegalArgumentException("Department already exists");
        }
        Department department = new Department(request.name(), request.code(), request.facultyId());
        department.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.chair(), request.phone(), request.email(), request.building(),
                request.facultyId(), true);
        return toResponse(departments.save(department));
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.DepartmentResponse> findAll(int page, int limit) {
        return departments.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.DepartmentResponse findOne(UUID id) {
        return toResponse(departments.findById(id).orElseThrow(() -> new IllegalArgumentException("Department not found")));
    }

    @Transactional
    public AcademicDtos.DepartmentResponse update(UUID id, AcademicDtos.UpdateDepartmentRequest request) {
        Department existing = departments.findById(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        existing.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.chair(), request.phone(), request.email(), request.building(),
                request.facultyId(), request.active());
        return toResponse(departments.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        departments.deleteById(id);
    }

    private AcademicDtos.DepartmentResponse toResponse(Department department) {
        return new AcademicDtos.DepartmentResponse(
                department.getId(), department.getName(), department.getNameEn(), department.getNameVi(),
                department.getCode(), department.getDescription(), department.getDescriptionEn(),
                department.getDescriptionVi(), department.getChair(), department.getPhone(),
                department.getEmail(), department.getBuilding(), department.getFacultyId(),
                department.isActive(), department.getCreatedAt(), department.getUpdatedAt());
    }
}
