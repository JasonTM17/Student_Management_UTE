package io.campuscore.people.service;

import io.campuscore.people.domain.Lecturer;
import io.campuscore.people.repository.LecturerRepository;
import io.campuscore.people.web.PeopleDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LecturerService {

    private final LecturerRepository lecturers;

    public LecturerService(LecturerRepository lecturers) {
        this.lecturers = lecturers;
    }

    @Transactional
    public PeopleDtos.LecturerResponse create(PeopleDtos.CreateLecturerRequest request) {
        if (lecturers.existsByEmployeeId(request.employeeId())) {
            throw new IllegalArgumentException("Lecturer profile already exists");
        }
        if (lecturers.existsByUserId(request.userId())) {
            throw new IllegalArgumentException("Lecturer profile already exists");
        }
        Lecturer lecturer = new Lecturer(
                request.userId(), request.email(), request.firstName(), request.lastName(),
                request.departmentId(), request.departmentCode(), request.departmentName(),
                request.employeeId(), request.title(), request.specialization(),
                request.office(), request.phone(),
                request.active() != null ? request.active() : true);
        return toResponse(lecturers.save(lecturer));
    }

    @Transactional(readOnly = true)
    public Page<PeopleDtos.LecturerResponse> findAll(int page, int limit) {
        return lecturers.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PeopleDtos.LecturerResponse findOne(UUID id) {
        return toResponse(lecturers.findById(id).orElseThrow(() -> new IllegalArgumentException("Lecturer not found")));
    }

    @Transactional
    public PeopleDtos.LecturerResponse update(UUID id, PeopleDtos.UpdateLecturerRequest request) {
        Lecturer existing = lecturers.findById(id).orElseThrow(() -> new IllegalArgumentException("Lecturer not found"));
        existing.updateFields(
                request.email(), request.firstName(), request.lastName(),
                request.departmentId(), request.departmentCode(), request.departmentName(),
                request.employeeId(), request.title(), request.specialization(),
                request.office(), request.phone(),
                request.active() != null ? request.active() : true);
        return toResponse(lecturers.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        lecturers.deleteById(id);
    }

    private PeopleDtos.LecturerResponse toResponse(Lecturer lecturer) {
        return new PeopleDtos.LecturerResponse(
                lecturer.getId(), lecturer.getUserId(), lecturer.getEmail(), lecturer.getFirstName(),
                lecturer.getLastName(), lecturer.getDepartmentId(), lecturer.getDepartmentCode(),
                lecturer.getDepartmentName(), lecturer.getEmployeeId(), lecturer.getTitle(),
                lecturer.getSpecialization(), lecturer.getOffice(), lecturer.getPhone(),
                lecturer.isActive(), lecturer.getCreatedAt(), lecturer.getUpdatedAt());
    }
}
