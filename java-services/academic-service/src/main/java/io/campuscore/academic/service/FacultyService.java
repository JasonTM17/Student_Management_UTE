package io.campuscore.academic.service;

import io.campuscore.academic.domain.Faculty;
import io.campuscore.academic.repository.FacultyRepository;
import io.campuscore.academic.web.AcademicDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacultyService {

    private final FacultyRepository faculties;

    public FacultyService(FacultyRepository faculties) {
        this.faculties = faculties;
    }

    @Transactional
    public AcademicDtos.FacultyResponse create(AcademicDtos.CreateFacultyRequest request) {
        if (faculties.existsByCode(request.code())) {
            throw new IllegalArgumentException("Faculty already exists");
        }
        Faculty faculty = new Faculty(request.name(), request.code());
        faculty.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.dean(), request.phone(), request.email(), request.building(), true);
        return toResponse(faculties.save(faculty));
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.FacultyResponse> findAll(int page, int limit) {
        return faculties.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.FacultyResponse findOne(UUID id) {
        return toResponse(faculties.findById(id).orElseThrow(() -> new IllegalArgumentException("Faculty not found")));
    }

    @Transactional
    public AcademicDtos.FacultyResponse update(UUID id, AcademicDtos.UpdateFacultyRequest request) {
        Faculty existing = faculties.findById(id).orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
        existing.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.description(), request.descriptionEn(), request.descriptionVi(),
                request.dean(), request.phone(), request.email(), request.building(), request.active());
        return toResponse(faculties.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        faculties.deleteById(id);
    }

    private AcademicDtos.FacultyResponse toResponse(Faculty faculty) {
        return new AcademicDtos.FacultyResponse(
                faculty.getId(), faculty.getName(), faculty.getNameEn(), faculty.getNameVi(),
                faculty.getCode(), faculty.getDescription(), faculty.getDescriptionEn(),
                faculty.getDescriptionVi(), faculty.getDean(), faculty.getPhone(), faculty.getEmail(),
                faculty.getBuilding(), faculty.isActive(), faculty.getCreatedAt(), faculty.getUpdatedAt());
    }
}
