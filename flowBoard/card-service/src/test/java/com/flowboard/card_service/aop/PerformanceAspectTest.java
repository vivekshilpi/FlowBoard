package com.flowboard.card_service.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceAspectTest {

    private final PerformanceAspect aspect = new PerformanceAspect();

    @Test
    void returnsProceedResultForFastCall() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("CardService.fast()");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenReturn("ok");

        assertThat(aspect.measureExecutionTime(pjp)).isEqualTo("ok");
    }

    @Test
    void returnsProceedResultForSlowCall() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("CardService.slow()");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenAnswer(invocation -> {
            Thread.sleep(550);
            return "slow";
        });

        assertThat(aspect.measureExecutionTime(pjp)).isEqualTo("slow");
    }
}
