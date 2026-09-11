package io.campuscore.restfulapi.security.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to customize rate limiting per Controller method or class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * Maximum allowed requests in the time window.
     */
    int limit() default 40;

    /**
     * Window duration in seconds.
     */
    int windowSeconds() default 60;

    /**
     * Semantic category for reporting and grouping.
     */
    RateLimitCategory category() default RateLimitCategory.DEFAULT_POST;
}
