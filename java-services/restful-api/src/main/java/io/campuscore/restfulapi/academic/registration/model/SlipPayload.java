package io.campuscore.restfulapi.academic.registration.model;

public record SlipPayload(String sha256, byte[] payload) {
}
