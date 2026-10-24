package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptSummary;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * <p>{@code thesis.eligibility.enforcement} accepts {@code ENFORCE} (default)
 * or {@code ADVISORY}; advisory mode still evaluates and reports but never
 * blocks a mutation. Bachelor round types (TLCN/KLTN) use the policy and any
 * unrecognised round type fails closed — it is treated as requiring
 * eligibility rather than exempted.
 */
@Service
@Profile("persistence")
public class ThesisEligibilityService {

    public static final String ERROR_CODE = "THESIS_ELIGIBILITY_NOT_MET";
    public static final String REASON_INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
    public static final String REASON_LOW_GPA = "LOW_GPA";
    public static final String REASON_ACADEMIC_DATA_UNAVAILABLE = "ACADEMIC_DATA_UNAVAILABLE";

    private final AcademicEnrollmentReadService academic;
    private final int minCredits;
    private final BigDecimal minCpa;
    private final String enforcement;

    public ThesisEligibilityService(
            AcademicEnrollmentReadService academic,
            @Value("${thesis.eligibility.min-credits:110}") int minCredits,
            @Value("${thesis.eligibility.min-cpa:2.0}") BigDecimal minCpa,
            @Value("${thesis.eligibility.enforcement:ENFORCE}") String enforcement) {
        this.academic = academic;
        this.minCredits = minCredits;
        this.minCpa = minCpa;
        this.enforcement = enforcement == null ? "ENFORCE" : enforcement.trim();
    }

    /** Per-student eligibility read model shared by enforcement and the UI. */
    public record EligibilityView(
            boolean eligible,
            int earnedCredits,
            int requiredCredits,
            BigDecimal cpa,
            BigDecimal requiredCpa,
            List<String> reasons) {
    }

    /**
     * Whether refusals apply. Anything but an explicit {@code ADVISORY} blocks,
     * so a misspelled or empty configuration fails closed (ENFORCE) rather
     * than silently disabling the regulation.
     */
    public boolean enforcementBlocks() {
        return !"ADVISORY".equalsIgnoreCase(enforcement);
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
                reasons.isEmpty(), earned, minCredits, cpa, minCpa, List.copyOf(reasons));
    }

    /**
     * Refuses a registration mutation for an ineligible student when
     * enforcement is ENFORCE. ADVISORY mode evaluates but never blocks.
     */
    public void requireEligible(String studentId, UUID roundId) {
        if (!enforcementBlocks()) {
            return;
        }
        EligibilityView view = evaluate(studentId);
        if (view.eligible()) {
            return;
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
     * admit an ineligible student, or null when the student is eligible.
     */
    public String describeIneligibility(String studentId) {
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
