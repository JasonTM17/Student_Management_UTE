package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicReadService;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal academic context reads guarded by X-Service-Token.
 *
 * <p>These routes stay disabled by default and are only enabled when the
 * migration.academic-context gate is switched on.</p>
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-context", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/internal/academic-context")
public class AcademicContextController {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String INVALID_TOKEN_MESSAGE =
            "Internal endpoint requires a valid X-Service-Token header";

    private final AcademicReadService academic;
    private final String internalServiceToken;

    public AcademicContextController(
            AcademicReadService academic,
            @Value("${INTERNAL_SERVICE_TOKEN:academic-internal-token-12345}") String internalServiceToken) {
        this.academic = academic;
        this.internalServiceToken = internalServiceToken;
    }

    @GetMapping("curricula/{curriculumId}")
    @ResponseBody
    public CurriculumResponse getCurriculum(
            @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @PathVariable String curriculumId) {
        requireServiceToken(serviceToken);
        return academic.findCurriculum(curriculumId);
    }

    @GetMapping("departments/{departmentId}")
    @ResponseBody
    public DepartmentResponse getDepartment(
            @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @PathVariable String departmentId) {
        requireServiceToken(serviceToken);
        return academic.findDepartment(departmentId);
    }

    private void requireServiceToken(String suppliedToken) {
        if (suppliedToken == null || suppliedToken.isBlank() || !internalServiceToken.equals(suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, INVALID_TOKEN_MESSAGE);
        }
    }
}
