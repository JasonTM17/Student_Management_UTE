package io.campuscore.academic.service;

import io.campuscore.academic.domain.Curriculum;
import io.campuscore.academic.repository.CurriculumRepository;
import io.campuscore.academic.web.AcademicDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurriculumService {

    private final CurriculumRepository curricula;

    public CurriculumService(CurriculumRepository curricula) {
        this.curricula = curricula;
    }

    @Transactional
    public AcademicDtos.CurriculumResponse create(AcademicDtos.CreateCurriculumRequest request) {
        if (curricula.existsByCode(request.code())) {
            throw new IllegalArgumentException("Curriculum already exists");
        }
        Curriculum curriculum = new Curriculum(request.name(), request.code(), request.departmentId(), request.credits());
        curriculum.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.departmentId(), request.departmentCode(), request.departmentName(),
                request.description(), request.credits(), true);
        return toResponse(curricula.save(curriculum));
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.CurriculumResponse> findAll(int page, int limit) {
        return curricula.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.CurriculumResponse findOne(UUID id) {
        return toResponse(curricula.findById(id).orElseThrow(() -> new IllegalArgumentException("Curriculum not found")));
    }

    @Transactional
    public AcademicDtos.CurriculumResponse update(UUID id, AcademicDtos.UpdateCurriculumRequest request) {
        Curriculum existing = curricula.findById(id).orElseThrow(() -> new IllegalArgumentException("Curriculum not found"));
        existing.updateFields(request.name(), request.nameEn(), request.nameVi(), request.code(),
                request.departmentId(), request.departmentCode(), request.departmentName(),
                request.description(), request.credits(), request.active());
        return toResponse(curricula.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        curricula.deleteById(id);
    }

    private AcademicDtos.CurriculumResponse toResponse(Curriculum curriculum) {
        return new AcademicDtos.CurriculumResponse(
                curriculum.getId(), curriculum.getName(), curriculum.getNameEn(), curriculum.getNameVi(),
                curriculum.getCode(), curriculum.getDepartmentId(), curriculum.getDepartmentCode(),
                curriculum.getDepartmentName(), curriculum.getDescription(), curriculum.getCredits(),
                curriculum.isActive(), curriculum.getCreatedAt(), curriculum.getUpdatedAt());
    }
}
