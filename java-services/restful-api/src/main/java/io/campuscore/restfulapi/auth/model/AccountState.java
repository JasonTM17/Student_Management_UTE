package io.campuscore.restfulapi.auth.model;

public record AccountState(String status, boolean mustChangePassword) {
}
