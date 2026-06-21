package com.example.foodrecommend.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("execution(public * com.example.foodrecommend.controller..*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String sig = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("→ {} args={}", sig, pjp.getArgs().length);
        try {
            Object ret = pjp.proceed();
            log.info("← {} {}ms", sig, System.currentTimeMillis() - start);
            return ret;
        } catch (Throwable t) {
            log.warn("✗ {} {}ms err={}", sig, System.currentTimeMillis() - start, t.getMessage());
            throw t;
        }
    }
}
