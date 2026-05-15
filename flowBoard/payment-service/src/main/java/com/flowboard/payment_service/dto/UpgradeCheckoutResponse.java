package com.flowboard.payment_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class UpgradeCheckoutResponse {
    private String sessionId;
    private String checkoutUrl;
    private BigDecimal proratedAmount;
    private Integer daysLeft;
    private String message;
}
