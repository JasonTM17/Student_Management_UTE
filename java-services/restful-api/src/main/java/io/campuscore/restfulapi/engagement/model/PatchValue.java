package io.campuscore.restfulapi.engagement.model;

public record PatchValue<T>(boolean present, T value) {

    public static <T> PatchValue<T> present(T value) {
        return new PatchValue<>(true, value);
    }

    public static <T> PatchValue<T> omitted() {
        return new PatchValue<>(false, null);
    }
}
