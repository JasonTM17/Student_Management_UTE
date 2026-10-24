package io.campuscore.restfulapi.notification.model;

public record PatchValue<T>(boolean present, T value) {

    public static <T> PatchValue<T> omitted() {
        return new PatchValue<>(false, null);
    }

    public static <T> PatchValue<T> present(T value) {
        return new PatchValue<>(true, value);
    }
}
