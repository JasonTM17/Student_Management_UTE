package io.campuscore.restfulapi.academic.model;

public record CourseBriefRow(
        String id,
        String code,
        String name,
        String nameEn,
        String nameVi,
        int credits) {
}
