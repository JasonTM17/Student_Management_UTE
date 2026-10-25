package io.campuscore.restfulapi.academic.model;

import java.util.Set;

public final class GradeAppealStatus {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> FILTERS = Set.of(PENDING, ACCEPTED, REJECTED, CANCELLED);

    private GradeAppealStatus() {
    }
}
