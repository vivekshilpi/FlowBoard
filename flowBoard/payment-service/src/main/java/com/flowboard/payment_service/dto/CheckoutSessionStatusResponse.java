package com.flowboard.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionStatusResponse {
    private String status; // PENDING | ACTIVE | FAILED
    private String planName;
    private String billingCycle;
    private String message;
}

