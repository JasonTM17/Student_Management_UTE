package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.dto.AcademicEnrollmentReadDtos.TranscriptSummary;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.model.EligibilityView;
import io.campuscore.restfulapi.thesis.model.RoundType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Thesis registration eligibility — the regulation's accumulated-credit
 * condition ('graduation-thesis-eligibility-defense' knowledge entry):
 * at least {@code thesis.eligibility.min-credits} accumulated credits
 * (default 110) and a cumulative GPA of at least
 * {@code thesis.eligibility.min-cpa} on the 4.0 scale (default 2.0).
 *
 * <p>Both numbers reuse the canonical transcript computation —
 * {@link AcademicEnrollmentReadService#findTranscriptSummaries}: published
 * grades only, best attempt per course, earned credits where the letter is
 * not 'F', weighted 4.0 GPA. The regulation's academic-warning and
 * disciplinary-sanction prerequisites have no data model in this schema and
 * are therefore not evaluated here.
 *
 * <p>The regulation text names graduation thesis only, so the policy applies
 * to the round types in {@code thesis.eligibility.round-types} (default
 * {@code KLTN}, comma-separated {@link RoundType} names). Other round types
 * carry no evaluation at all: mutations are never blocked and the read model
 * reports {@code null} eligibility. Missing academic data fails closed only
 * inside the covered types.
 *
 * <p>{@code thesis.eligibility.enforcement} accepts {@code ENFORCE} or
 * {@code ADVISORY} (default); advisory mode still evaluates and reports but
 * never blocks a mutation.
 */
@Service
@Profile("persistence")
public class ThesisEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(ThesisEligibilityService.class);

    public static final String ERROR_CODE = "THESIS_ELIGIBILITY_NOT_MET";
    public static final String REASON_INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
    public static final String REASON_LOW_GPA = "LOW_GPA";
    public static final String REASON_ACADEMIC_DATA_UNAVAILABLE = "ACADEMIC_DATA_UNAVAILABLE";

    private final AcademicEnrollmentReadService academic;
    private final int minCredits;
    private final BigDecimal minCpa;
    private final boolean enforce;
    private final Set<RoundType> coveredRoundTypes;

    public ThesisEligibilityService(
            AcademicEnrollmentReadService academic,
            @Value("${thesis.eligibility.min-credits:110}") int minCredits,
            @Value("${thesis.eligibility.min-cpa:2.0}") BigDecimal minCpa,
            @Value("${thesis.eligibility.enforcement:ADVISORY}") String enforcement,
            @Value("${thesis.eligibility.round-types:KLTN}") String roundTypes) {
        this.academic = academic;
        this.minCredits = minCredits;
        this.minCpa = minCpa;
        this.enforce = parseEnforcement(enforcement);
        this.coveredRoundTypes = parseRoundTypes(roundTypes);
    }

    // An unrecognized value degrades to ADVISORY: a config typo must never
    // start refusing registrations during a staged rollout.
    private static boolean parseEnforcement(String enforcement) {
        String value = enforcement == null ? "" : enforcement.trim();
        if ("ENFORCE".equalsIgnoreCase(value)) {
            return true;
        }
        if (!"ADVISORY".equalsIgnoreCase(value)) {
            log.warn("Unrecognized thesis.eligibility.enforcement '{}'; behaving as ADVISORY", value);
        }
        return false;
    }

    private static Set<RoundType> parseRoundTypes(String roundTypes) {
        Set<RoundType> covered = EnumSet.noneOf(RoundType.class);
        if (roundTypes != null) {
            for (String token : roundTypes.split(",")) {
                String name = token.trim();
                if (name.isEmpty()) {
                    continue;
                }
                RoundType parsed = roundType(name);
                if (parsed == null) {
                    log.warn("Unrecognized thesis.eligibility.round-types entry '{}'; ignoring it", name);
                } else {
                    covered.add(parsed);
                }
            }
        }
        return Set.copyOf(covered);
    }

    private static RoundType roundType(String thesisType) {
        if (thesisType == null) {
            return null;
        }
        for (RoundType type : RoundType.values()) {
            if (type.name().equalsIgnoreCase(thesisType.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * Whether refusals apply: true only for an explicit {@code ENFORCE}
     * configuration; anything else (ADVISORY, blank, unrecognized) behaves
     * as advisory.
     */
    public boolean enforcementBlocks() {
        return enforce;
    }

    /**
     * Whether the credit/CPA policy governs the given round type — only the
     * types named by {@code thesis.eligibility.round-types} (default KLTN,
     * the only type the regulation covers).
     */
    public boolean covers(String thesisType) {
        RoundType type = roundType(thesisType);
        return type != null && coveredRoundTypes.contains(type);
    }

    /**
     * Whether this round type actively blocks registrations — used for the
     * read model's {@code enforced} flag so clients can warn without blocking
     * in ADVISORY mode.
     */
    public boolean enforced(String thesisType) {
        return enforce && covers(thesisType);
    }

    /**
     * Caller-scoped eligibility for one round: null on round types the policy
     * does not cover, otherwise the evaluated view with its {@code enforced}
     * flag.
     */
    public EligibilityView viewFor(String studentId, String thesisType) {
        if (!covers(thesisType)) {
            return null;
        }
        return evaluate(studentId);
    }

    /**
     * Evaluates one student's transcript against the configured thresholds.
     * Missing or empty published academic data fails closed: the student is
     * reported ineligible with {@link #REASON_ACADEMIC_DATA_UNAVAILABLE}.
     */
    public EligibilityView evaluate(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return evaluate((TranscriptSummary) null);
        }
        return evaluateAll(List.of(studentId)).get(studentId);
    }

    /**
     * Batch evaluation — one published-grade query for the whole roster.
     * Students absent from the map keys are evaluated as having no data.
     */
    public Map<String, EligibilityView> evaluateAll(Collection<String> studentIds) {
        Map<String, EligibilityView> views = new LinkedHashMap<>();
        if (studentIds == null || studentIds.isEmpty()) {
            return views;
        }
        Map<String, TranscriptSummary> summaries = academic.findTranscriptSummaries(studentIds);
        for (String studentId : studentIds) {
            views.put(studentId, evaluate(summaries.get(studentId)));
        }
        return views;
    }

    private EligibilityView evaluate(TranscriptSummary summary) {
        List<String> reasons = new ArrayList<>();
        int earned = summary == null ? 0 : summary.totalCreditsEarned();
        BigDecimal cpa = summary == null ? null : summary.cumulativeGpa();
        boolean hasData = summary != null && summary.totalCreditsAttempted() > 0;
        if (!hasData) {
            reasons.add(REASON_ACADEMIC_DATA_UNAVAILABLE);
        }
        if (earned < minCredits) {
            reasons.add(REASON_INSUFFICIENT_CREDITS);
        }
        if (hasData && (cpa == null || cpa.compareTo(minCpa) < 0)) {
            reasons.add(REASON_LOW_GPA);
        }
        return new EligibilityView(
                reasons.isEmpty(), earned, minCredits, cpa, minCpa,
                List.copyOf(reasons), enforce);
    }

    /**
     * Refuses a registration mutation for an ineligible student on a covered
     * round type while enforcement is ENFORCE. ADVISORY mode and uncovered
     * round types evaluate but never block.
     *
     * <p>{@code discloseFigures} must be true only when the caller is the
     * evaluated student: the figures belong to that student, so the error for
     * any other caller keeps the code and reasons but drops every number.
     */
    public void requireEligible(String studentId, UUID roundId, String thesisType,
            boolean discloseFigures) {
        if (!enforced(thesisType)) {
            return;
        }
        EligibilityView view = evaluate(studentId);
        if (view.eligible()) {
            return;
        }
        if (!discloseFigures) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reasons", view.reasons());
            details.put("roundId", roundId == null ? null : roundId.toString());
            throw new DomainException(HttpStatus.CONFLICT, ERROR_CODE,
                    "This student does not meet the thesis registration requirements.",
                    details);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("earnedCredits", view.earnedCredits());
        details.put("requiredCredits", view.requiredCredits());
        details.put("cpa", view.cpa());
        details.put("requiredCpa", view.requiredCpa());
        details.put("reasons", view.reasons());
        details.put("roundId", roundId == null ? null : roundId.toString());
        throw new DomainException(HttpStatus.CONFLICT, ERROR_CODE,
                "Thesis registration requires at least " + view.requiredCredits()
                        + " accumulated credits and a cumulative GPA of " + view.requiredCpa()
                        + "; this student has " + view.earnedCredits() + " credits"
                        + (view.cpa() == null ? " and no published GPA yet" : " and a GPA of " + view.cpa()),
                details);
    }

    /**
     * One-line audit note for governance escape hatches that deliberately
     * admit an ineligible student, or null when the student is eligible or
     * the round type is not covered by the policy.
     */
    public String describeIneligibility(String studentId, String thesisType) {
        if (!covers(thesisType)) {
            return null;
        }
        EligibilityView view = evaluate(studentId);
        if (view == null || view.eligible()) {
            return null;
        }
        return "student does not meet thesis eligibility ("
                + view.earnedCredits() + "/" + view.requiredCredits() + " credits"
                + (view.cpa() == null ? ", no published GPA" : ", GPA " + view.cpa() + "/" + view.requiredCpa())
                + ")";
    }
}
