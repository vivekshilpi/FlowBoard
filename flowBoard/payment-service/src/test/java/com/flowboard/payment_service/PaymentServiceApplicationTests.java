package com.flowboard.payment_service;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class PaymentServiceApplicationTests {

    @Test
    void mainInvokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            PaymentServiceApplication.main(new String[0]);
            springApplication.verify(() -> SpringApplication.run(PaymentServiceApplication.class, new String[0]));
        }
    }
}
