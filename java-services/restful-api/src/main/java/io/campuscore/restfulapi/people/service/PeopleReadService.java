package io.campuscore.restfulapi.people.service;

import io.campuscore.restfulapi.people.repository.PeopleReadRepository;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.PageMeta;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentResponse;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Student and lecturer profile query service. */
@Service
@Profile("persistence")
public class PeopleReadService {

    public static final int MAX_PAGE_SIZE = 100;

    private final PeopleReadRepository people;

    public PeopleReadService(PeopleReadRepository people) {
        this.people = people;
    }

    @Transactional(readOnly = true)
    public StudentListResponse findStudents(int page, int limit, String status) {
        requirePage(page, limit);
        String normalizedStatus = normalizeStatus(status);
        long total = people.countStudents(normalizedStatus);
        List<StudentResponse> data = people.findStudents(offset(page, limit), limit, normalizedStatus);
        return new StudentListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public StudentResponse findStudent(String id) {
        return findStudent(id, null);
    }

    @Transactional(readOnly = true)
    public StudentResponse findStudent(String id, Authentication authentication) {
        String ownerId = studentOnly(authentication) ? authentication.getName() : null;
        if (ownerId != null) {
            return people.findStudentByIdAndUserId(id, ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        }
        return people.findStudentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private static boolean studentOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        return authorities.contains("ROLE_STUDENT")
                && !authorities.contains("ROLE_ADMIN")
                && !authorities.contains("ROLE_SUPER_ADMIN")
                && !authorities.contains("ROLE_LECTURER");
    }

    @Transactional(readOnly = true)
    public LecturerListResponse findLecturers(int page, int limit) {
        requirePage(page, limit);
        long total = people.countLecturers();
        List<LecturerResponse> data = people.findLecturers(offset(page, limit), limit);
        return new LecturerListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public LecturerResponse findLecturer(String id) {
        return people.findLecturerById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        return trimmed;
    }

    private static PageMeta meta(long total, int page, int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("People result is too large");
        }
        return new PageMeta(total, page, limit, (int) totalPages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
