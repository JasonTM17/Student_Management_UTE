package io.campuscore.people.service;

import io.campuscore.people.domain.Student;
import io.campuscore.people.repository.StudentRepository;
import io.campuscore.people.web.PeopleDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository students;

    public StudentService(StudentRepository students) {
        this.students = students;
    }

    @Transactional
    public PeopleDtos.StudentResponse create(PeopleDtos.CreateStudentRequest request) {
        if (students.existsByStudentId(request.studentId())) {
            throw new IllegalArgumentException("Student profile already exists");
        }
        if (students.existsByUserId(request.userId())) {
            throw new IllegalArgumentException("Student profile already exists");
        }
        Student student = new Student(
                request.userId(), request.email(), request.firstName(), request.lastName(),
                request.studentId(), request.curriculumId(), request.curriculumCode(),
                request.curriculumName(), request.departmentId(), request.departmentCode(),
                request.departmentName(), request.year(),
                request.status() != null ? request.status() : "ACTIVE",
                request.admissionDate());
        return toResponse(students.save(student));
    }

    @Transactional(readOnly = true)
    public Page<PeopleDtos.StudentResponse> findAll(int page, int limit, String status) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100));
        if (status != null && !status.isBlank()) {
            return students.findByStatusOrderByCreatedAtDesc(status, pageRequest).map(this::toResponse);
        }
        return students.findAllByOrderByCreatedAtDesc(pageRequest).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PeopleDtos.StudentResponse findOne(UUID id) {
        return toResponse(students.findById(id).orElseThrow(() -> new IllegalArgumentException("Student not found")));
    }

    @Transactional
    public PeopleDtos.StudentResponse update(UUID id, PeopleDtos.UpdateStudentRequest request) {
        Student existing = students.findById(id).orElseThrow(() -> new IllegalArgumentException("Student not found"));
        existing.updateFields(
                request.email(), request.firstName(), request.lastName(), request.studentId(),
                request.curriculumId(), request.curriculumCode(), request.curriculumName(),
                request.departmentId(), request.departmentCode(), request.departmentName(),
                request.year(), request.status(), request.admissionDate());
        return toResponse(students.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        students.deleteById(id);
    }

    private PeopleDtos.StudentResponse toResponse(Student student) {
        return new PeopleDtos.StudentResponse(
                student.getId(), student.getUserId(), student.getEmail(), student.getFirstName(),
                student.getLastName(), student.getStudentId(), student.getCurriculumId(),
                student.getCurriculumCode(), student.getCurriculumName(), student.getDepartmentId(),
                student.getDepartmentCode(), student.getDepartmentName(), student.getYear(),
                student.getStatus(), student.getAdmissionDate(), student.getCreatedAt(),
                student.getUpdatedAt());
    }
}
