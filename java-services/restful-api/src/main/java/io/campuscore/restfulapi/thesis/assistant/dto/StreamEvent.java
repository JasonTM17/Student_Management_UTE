package io.campuscore.restfulapi.thesis.assistant.dto;

public sealed interface StreamEvent permits StreamMeta, StreamDelta, StreamReplace, StreamCitation, StreamDone, StreamError { }
