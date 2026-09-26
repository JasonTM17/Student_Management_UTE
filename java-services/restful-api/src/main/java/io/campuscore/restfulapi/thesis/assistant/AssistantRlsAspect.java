package io.campuscore.restfulapi.thesis.assistant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Makes every marked Assistant SQL boundary enter its transaction before invoking application code. */
@Aspect
@Component
@Profile("persistence")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AssistantRlsAspect {
    private final AssistantRlsTransactionRunner transactions;

    public AssistantRlsAspect(AssistantRlsTransactionRunner transactions) {
        this.transactions = transactions;
    }

    @Around("@within(io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary) "
            + "|| @annotation(io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary)")
    public Object enforceBoundary(ProceedingJoinPoint invocation) throws Throwable {
        MethodSignature signature = (MethodSignature) invocation.getSignature();
        AssistantRlsBoundary boundary = AnnotatedElementUtils.findMergedAnnotation(
                signature.getMethod(), AssistantRlsBoundary.class);
        if (boundary == null) {
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(invocation.getTarget());
            boundary = AnnotatedElementUtils.findMergedAnnotation(targetClass, AssistantRlsBoundary.class);
        }
        if (boundary == null) {
            throw new IllegalStateException("Marked Assistant boundary has no access scope");
        }
        return transactions.execute(boundary.access(), invocation::proceed);
    }
}
