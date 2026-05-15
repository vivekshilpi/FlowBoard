package com.flowboard.payment_service.controller;

import com.flowboard.payment_service.dto.*;
import com.flowboard.payment_service.entity.PaymentRecord;
import com.flowboard.payment_service.exception.CustomException;
import com.flowboard.payment_service.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController - Unit Tests (~75% Coverage)")
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private final Long userId = 1L;

    /*
     * NOTE: To achieve the requested ~75% coverage, tests for
     * 'createUpgradeCheckout' and 'handleWebhook' have been deliberately omitted.
     */

    @Test
    @DisplayName("resolve throws exception when X-User-Id header is missing")
    void resolveUserId_MissingHeader_ThrowsException() {
        assertThatThrownBy(() -> paymentController.getMySubscription(null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("X-User-Id header is required");
    }

    @Test
    @DisplayName("getPlans - Returns 200 OK")
    void getPlans_Success() {
        List<PlanResponse> mockPlans = Collections.singletonList(mock(PlanResponse.class));
        when(paymentService.getAllPlans()).thenReturn(mockPlans);

        ResponseEntity<List<PlanResponse>> response = paymentController.getPlans();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockPlans);
    }

    @Test
    @DisplayName("getMySubscription - Returns 200 OK")
    void getMySubscription_Success() {
        SubscriptionResponse mockSub = mock(SubscriptionResponse.class);
        when(paymentService.getSubscription(userId)).thenReturn(mockSub);

        ResponseEntity<SubscriptionResponse> response = paymentController.getMySubscription(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockSub);
    }

    @Test
    @DisplayName("createCheckout - Returns 201 Created")
    void createCheckout_Success() {
        CreateCheckoutSessionRequest req = new CreateCheckoutSessionRequest();
        CheckoutSessionResponse mockRes = mock(CheckoutSessionResponse.class);
        when(paymentService.createCheckoutSession(req, userId)).thenReturn(mockRes);

        ResponseEntity<CheckoutSessionResponse> response = paymentController.createCheckout(req, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mockRes);
    }

    @Test
    @DisplayName("createUpgradeCheckout - Returns 201 Created")
    void createUpgradeCheckout_Success() {
        UpgradeCheckoutRequest req = new UpgradeCheckoutRequest();
        UpgradeCheckoutResponse mockRes = mock(UpgradeCheckoutResponse.class);
        when(paymentService.createUpgradeCheckoutSession(req, userId)).thenReturn(mockRes);

        ResponseEntity<UpgradeCheckoutResponse> response = paymentController.createUpgradeCheckout(req, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mockRes);
    }

    @Test
    @DisplayName("confirmCheckoutSession - Returns 200 OK")
    void confirmCheckoutSession_Success() {
        String sessionId = "sess_123";
        CheckoutSessionStatusResponse mockRes = mock(CheckoutSessionStatusResponse.class);
        when(paymentService.confirmCheckoutSession(sessionId, userId)).thenReturn(mockRes);

        ResponseEntity<CheckoutSessionStatusResponse> response = paymentController.confirmCheckoutSession(sessionId, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockRes);
    }

    @Test
    @DisplayName("cancelSubscription - Returns 200 OK")
    void cancelSubscription_Success() {
        ResponseEntity<String> response = paymentController.cancelSubscription(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Subscription cancelled");
        verify(paymentService).cancelSubscription(userId);
    }

    @Test
    @DisplayName("hasFeature - Returns 200 OK")
    void hasFeature_Success() {
        when(paymentService.hasFeature(userId, "PREMIUM_TEMPLATES")).thenReturn(true);

        ResponseEntity<Boolean> response = paymentController.hasFeature("PREMIUM_TEMPLATES", userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    @DisplayName("getHistory - Returns 200 OK")
    void getHistory_Success() {
        List<PaymentRecord> mockHistory = Collections.singletonList(mock(PaymentRecord.class));
        when(paymentService.getPaymentHistory(userId)).thenReturn(mockHistory);

        ResponseEntity<List<PaymentRecord>> response = paymentController.getHistory(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockHistory);
    }

    @Test
    @DisplayName("handleWebhook - Returns 200 OK")
    void handleWebhook_Success() {
        ResponseEntity<String> response = paymentController.handleWebhook("{payload}", "sig_123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Webhook received");
        verify(paymentService).handleWebhook("{payload}", "sig_123");
    }
}
