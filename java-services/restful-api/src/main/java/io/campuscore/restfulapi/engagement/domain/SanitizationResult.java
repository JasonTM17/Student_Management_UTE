package io.campuscore.restfulapi.engagement.domain;

/** Sanitized body plus how many elements the policy removed (observability). */
public record SanitizationResult(String html, int removedElements) {}
