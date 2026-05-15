package com.flowboard.payment_service.dto;

import lombok.Data;

@Data
public class UpgradeCheckoutRequest {
    private String targetPlanName;
    private String billingCycle;
}
