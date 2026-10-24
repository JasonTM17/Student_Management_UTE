package io.campuscore.restfulapi.thesis.assistant.model;

public record RemotePage<T>(T data, String nextCursor) { }
