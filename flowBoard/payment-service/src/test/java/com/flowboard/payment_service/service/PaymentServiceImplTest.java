package com.flowboard.payment_service.service;

import com.flowboard.payment_service.dto.CheckoutSessionResponse;
import com.flowboard.payment_service.dto.CheckoutSessionStatusResponse;
import com.flowboard.payment_service.dto.CreateCheckoutSessionRequest;
import com.flowboard.payment_service.dto.PlanResponse;
import com.flowboard.payment_service.dto.SubscriptionResponse;
import com.flowboard.payment_service.dto.UpgradeCheckoutRequest;
import com.flowboard.payment_service.dto.UpgradeCheckoutResponse;
import com.flowboard.payment_service.entity.PaymentRecord;
import com.flowboard.payment_service.entity.Plan;
import com.flowboard.payment_service.entity.Subscription;
import com.flowboard.payment_service.exception.CustomException;
import com.flowboard.payment_service.repository.PaymentRecordRepository;
import com.flowboard.payment_service.repository.PlanRepository;
import com.flowboard.payment_service.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl unit tests")
class PaymentServiceImplTest {

    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRecordRepository paymentRecordRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final Long USER_ID = 100L;

    private Plan proPlan;
    private Plan businessPlan;
    private Subscription activeSubscription;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "sk_test_12345");
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "whsec_12345");
        paymentService.init();

        proPlan = Plan.builder()
                .id(2L)
                .name("PRO")
                .displayName("Pro")
                .priceMonthly(new BigDecimal("9.99"))
                .priceYearly(new BigDecimal("99.00"))
                .stripePriceIdMonthly("price_monthly_mock")
                .stripePriceIdYearly("price_yearly_mock")
                .hasAdvancedAnalytics(true)
                .build();

        businessPlan = Plan.builder()
                .id(3L)
                .name("BUSINESS")
                .displayName("Business")
                .priceMonthly(new BigDecimal("29.99"))
                .priceYearly(new BigDecimal("299.00"))
                .stripePriceIdMonthly("price_business_monthly")
                .stripePriceIdYearly("price_business_yearly")
                .hasAdvancedAnalytics(true)
                .hasPrioritySupport(true)
                .hasCustomFields(true)
                .hasAutomation(true)
                .build();

        activeSubscription = Subscription.builder()
                .id(1L)
                .userId(USER_ID)
                .plan(proPlan)
                .status("ACTIVE")
                .billingCycle("MONTHLY")
                .stripeCustomerId("cus_123")
                .stripeSubscriptionId("sub_123")
                .currentPeriodStart(LocalDate.now().minusDays(10))
                .currentPeriodEnd(LocalDate.now().plusDays(20))
                .build();
    }

    @Test
    void getAllPlansReturnsMappedPlans() {
        when(planRepository.count()).thenReturn(1L);
        when(planRepository.findAll()).thenReturn(List.of(proPlan));

        List<PlanResponse> result = paymentService.getAllPlans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("PRO");
    }

    @Test
    void getAllPlansSeedsDefaultsWhenRepositoryIsEmpty() {
        when(planRepository.count()).thenReturn(0L);
        when(planRepository.findAll()).thenReturn(List.of(proPlan));

        paymentService.getAllPlans();

        verify(planRepository).saveAll(any());
    }

    @Test
    void getSubscriptionReturnsExistingSubscription() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        SubscriptionResponse result = paymentService.getSubscription(USER_ID);

        assertThat(result.getPlanName()).isEqualTo("PRO");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getSubscriptionCreatesFreeSubscriptionWhenMissing() {
        Plan freePlan = Plan.builder().name("FREE").displayName("Free").build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(planRepository.count()).thenReturn(1L);
        when(planRepository.findByName("FREE")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse result = paymentService.getSubscription(USER_ID);

        assertThat(result.getPlanName()).isEqualTo("FREE");
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void createCheckoutSessionReturnsStripeSession() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setPlanId(proPlan.getId());
        request.setBillingCycle("MONTHLY");

        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getId()).thenReturn("sess_123");
            when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/123");
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(stripeSession);

            CheckoutSessionResponse response = paymentService.createCheckoutSession(request, USER_ID);

            assertThat(response.getSessionId()).isEqualTo("sess_123");
            assertThat(response.getCheckoutUrl()).contains("checkout.stripe.com");
        }
    }

    @Test
    void createCheckoutSessionFallsBackToDynamicPriceDataWhenConfiguredPriceIsMissing() throws Exception {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setPlanId(proPlan.getId());
        request.setBillingCycle("YEARLY");

        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        StripeException missingPrice = mock(StripeException.class);
        when(missingPrice.getMessage()).thenReturn("No such price: price_business_yearly");

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session fallbackSession = mock(Session.class);
            when(fallbackSession.getId()).thenReturn("sess_fallback");
            when(fallbackSession.getUrl()).thenReturn("https://checkout.stripe.com/fallback");

            mockedSession.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(missingPrice)
                    .thenReturn(fallbackSession);

            CheckoutSessionResponse response = paymentService.createCheckoutSession(request, USER_ID);

            assertThat(response.getSessionId()).isEqualTo("sess_fallback");
            assertThat(response.getCheckoutUrl()).contains("fallback");
        }
    }

    @Test
    void createCheckoutSessionThrowsWhenPlanIsMissing() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setPlanId(999L);
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createCheckoutSession(request, USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Plan not found");
    }

    @Test
    void createCheckoutSessionThrowsForInvalidStripePriceId() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setPlanId(proPlan.getId());
        request.setBillingCycle("MONTHLY");
        proPlan.setStripePriceIdMonthly("bad_id");

        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(request, USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Invalid Stripe price ID");
    }

    @Test
    void createCheckoutSessionWrapsStripeFailure() throws Exception {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setPlanId(proPlan.getId());
        request.setBillingCycle("MONTHLY");

        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        StripeException stripeException = mock(StripeException.class);
        when(stripeException.getMessage()).thenReturn("Stripe unavailable");

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> paymentService.createCheckoutSession(request, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Payment gateway error: Stripe unavailable");
        }
    }

    @Test
    void createUpgradeCheckoutSessionRejectsMissingSubscription() {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createUpgradeCheckoutSession(request, USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("No subscription found");
    }

    @Test
    void createUpgradeCheckoutSessionRejectsInactiveSubscription() {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();
        activeSubscription.setStatus("PAST_DUE");
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        assertThatThrownBy(() -> paymentService.createUpgradeCheckoutSession(request, USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Subscription must be active");
    }

    @Test
    void createUpgradeCheckoutSessionRejectsNonBusinessTarget() {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();
        request.setTargetPlanName("PRO");
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        assertThatThrownBy(() -> paymentService.createUpgradeCheckoutSession(request, USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Only Business upgrades are supported");
    }

    @Test
    void createUpgradeCheckoutSessionDirectlyActivatesBusinessWhenProrationIsZero() {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();
        Plan freeUpgradeBusiness = Plan.builder()
                .id(3L)
                .name("BUSINESS")
                .displayName("Business")
                .priceMonthly(proPlan.getPriceMonthly())
                .build();

        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));
        when(planRepository.findByName("BUSINESS")).thenReturn(Optional.of(freeUpgradeBusiness));

        UpgradeCheckoutResponse response = paymentService.createUpgradeCheckoutSession(request, USER_ID);

        assertThat(response.getProratedAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getMessage()).contains("without additional charge");
        verify(subscriptionRepository).save(activeSubscription);
        assertThat(activeSubscription.getPlan().getName()).isEqualTo("BUSINESS");
    }

    @Test
    void createUpgradeCheckoutSessionCreatesProratedCheckout() {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();

        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));
        when(planRepository.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getId()).thenReturn("sess_upgrade");
            when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/upgrade");
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(stripeSession);

            UpgradeCheckoutResponse response = paymentService.createUpgradeCheckoutSession(request, USER_ID);

            assertThat(response.getSessionId()).isEqualTo("sess_upgrade");
            assertThat(response.getCheckoutUrl()).contains("upgrade");
            assertThat(response.getProratedAmount()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    void createUpgradeCheckoutSessionWrapsStripeErrors() throws Exception {
        UpgradeCheckoutRequest request = new UpgradeCheckoutRequest();

        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));
        when(planRepository.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));

        StripeException stripeException = mock(StripeException.class);
        when(stripeException.getMessage()).thenReturn("upgrade failed");

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> paymentService.createUpgradeCheckoutSession(request, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Payment gateway error: upgrade failed");
        }
    }

    @Test
    void confirmCheckoutSessionActivatesSubscription() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getPaymentStatus()).thenReturn("paid");
            when(stripeSession.getStatus()).thenReturn("complete");
            when(stripeSession.getMetadata()).thenReturn(Map.of(
                    "userId", String.valueOf(USER_ID),
                    "planId", String.valueOf(proPlan.getId()),
                    "billingCycle", "MONTHLY"
            ));
            when(stripeSession.getCustomer()).thenReturn("cus_new");
            when(stripeSession.getSubscription()).thenReturn("sub_new");

            mockedSession.when(() -> Session.retrieve("sess_123")).thenReturn(stripeSession);
            when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
            when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_123", USER_ID);

            assertThat(response.getStatus()).isEqualTo("ACTIVE");
            verify(subscriptionRepository).save(activeSubscription);
        }
    }

    @Test
    void confirmCheckoutSessionReturnsFailedWhenSessionIsMissing() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.retrieve("missing")).thenReturn(null);

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("missing", USER_ID);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getMessage()).contains("not found");
        }
    }

    @Test
    void confirmCheckoutSessionReturnsFailedForWrongUser() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getMetadata()).thenReturn(Map.of("userId", "999"));
            mockedSession.when(() -> Session.retrieve("sess_wrong")).thenReturn(stripeSession);

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_wrong", USER_ID);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getMessage()).contains("does not belong");
        }
    }

    @Test
    void confirmCheckoutSessionReturnsPendingForIncompleteCheckout() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getPaymentStatus()).thenReturn("unpaid");
            when(stripeSession.getStatus()).thenReturn("open");
            when(stripeSession.getMetadata()).thenReturn(Map.of(
                    "userId", String.valueOf(USER_ID),
                    "planId", String.valueOf(proPlan.getId())
            ));
            when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));

            mockedSession.when(() -> Session.retrieve("sess_pending")).thenReturn(stripeSession);

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_pending", USER_ID);

            assertThat(response.getStatus()).isEqualTo("PENDING");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    void confirmCheckoutSessionActivatesProratedUpgrade() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getPaymentStatus()).thenReturn("paid");
            when(stripeSession.getStatus()).thenReturn("complete");
            when(stripeSession.getMetadata()).thenReturn(Map.of(
                    "userId", String.valueOf(USER_ID),
                    "planId", String.valueOf(proPlan.getId()),
                    "billingCycle", "MONTHLY",
                    "upgradeType", "PRO_TO_BUSINESS_PRORATED",
                    "toPlan", "BUSINESS"
            ));
            mockedSession.when(() -> Session.retrieve("sess_upgrade")).thenReturn(stripeSession);

            when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));
            when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
            when(planRepository.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_upgrade", USER_ID);

            assertThat(response.getStatus()).isEqualTo("ACTIVE");
            assertThat(response.getPlanName()).isEqualTo("BUSINESS");
            verify(subscriptionRepository).save(activeSubscription);
            assertThat(activeSubscription.getPlan().getName()).isEqualTo("BUSINESS");
        }
    }

    @Test
    void confirmCheckoutSessionReturnsFailedForStripeErrors() throws Exception {
        StripeException stripeException = mock(StripeException.class);
        when(stripeException.getMessage()).thenReturn("session lookup failed");

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.retrieve("sess_error")).thenThrow(stripeException);

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_error", USER_ID);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getMessage()).contains("session lookup failed");
        }
    }

    @Test
    void confirmCheckoutSessionReturnsFailedForUnexpectedErrors() {
        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            Session stripeSession = mock(Session.class);
            when(stripeSession.getPaymentStatus()).thenReturn("paid");
            when(stripeSession.getStatus()).thenReturn("complete");
            when(stripeSession.getMetadata()).thenReturn(Map.of("userId", String.valueOf(USER_ID), "planId", "oops"));
            mockedSession.when(() -> Session.retrieve("sess_broken")).thenReturn(stripeSession);

            CheckoutSessionStatusResponse response = paymentService.confirmCheckoutSession("sess_broken", USER_ID);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getMessage()).contains("Unable to confirm payment");
        }
    }

    @Test
    void handleWebhookRejectsInvalidSignature() throws Exception {
        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345"))
                    .thenThrow(new SignatureVerificationException("bad sig", "sig"));

            assertThatThrownBy(() -> paymentService.handleWebhook("payload", "sig"))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Invalid webhook signature");
        }
    }

    @Test
    void handleWebhookActivatesCheckoutSubscription() throws Exception {
        Session session = mock(Session.class);
        when(session.getMetadata()).thenReturn(Map.of(
                "userId", String.valueOf(USER_ID),
                "planId", String.valueOf(proPlan.getId()),
                "billingCycle", "MONTHLY"
        ));
        when(session.getCustomer()).thenReturn("cus_new");
        when(session.getSubscription()).thenReturn("sub_new");

        Event event = checkoutEvent("checkout.session.completed", session);
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345")).thenReturn(event);

            paymentService.handleWebhook("payload", "sig");

            verify(subscriptionRepository).save(activeSubscription);
        }
    }

    @Test
    void handleWebhookActivatesProratedUpgrade() throws Exception {
        Session session = mock(Session.class);
        when(session.getMetadata()).thenReturn(Map.of(
                "userId", String.valueOf(USER_ID),
                "upgradeType", "PRO_TO_BUSINESS_PRORATED",
                "toPlan", "BUSINESS"
        ));
        Event event = checkoutEvent("checkout.session.completed", session);

        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));
        when(planRepository.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));

        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345")).thenReturn(event);

            paymentService.handleWebhook("payload", "sig");

            verify(subscriptionRepository).save(activeSubscription);
            assertThat(activeSubscription.getPlan().getName()).isEqualTo("BUSINESS");
        }
    }

    @Test
    void handleWebhookRecordsSuccessfulInvoicePayments() throws Exception {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getCustomer()).thenReturn("cus_123");
        when(invoice.getPaymentIntent()).thenReturn("pi_123");
        when(invoice.getAmountPaid()).thenReturn(2999L);
        when(invoice.getCurrency()).thenReturn("usd");
        when(invoice.getId()).thenReturn("in_123");

        Event event = checkoutEvent("invoice.payment_succeeded", invoice);
        when(subscriptionRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345")).thenReturn(event);

            paymentService.handleWebhook("payload", "sig");

            verify(subscriptionRepository).save(activeSubscription);
            ArgumentCaptor<PaymentRecord> recordCaptor = ArgumentCaptor.forClass(PaymentRecord.class);
            verify(paymentRecordRepository).save(recordCaptor.capture());
            assertThat(recordCaptor.getValue().getStatus()).isEqualTo("SUCCEEDED");
        }
    }

    @Test
    void handleWebhookMarksSubscriptionPastDueWhenInvoiceFails() throws Exception {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getCustomer()).thenReturn("cus_123");
        when(invoice.getPaymentIntent()).thenReturn("pi_456");
        when(invoice.getAmountPaid()).thenReturn(0L);
        when(invoice.getCurrency()).thenReturn("usd");
        when(invoice.getId()).thenReturn("in_456");

        Event event = checkoutEvent("invoice.payment_failed", invoice);
        when(subscriptionRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345")).thenReturn(event);

            paymentService.handleWebhook("payload", "sig");

            assertThat(activeSubscription.getStatus()).isEqualTo("PAST_DUE");
            verify(subscriptionRepository).save(activeSubscription);
        }
    }

    @Test
    void handleWebhookCancelsDeletedStripeSubscription() throws Exception {
        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        when(stripeSubscription.getId()).thenReturn("sub_123");
        Event event = checkoutEvent("customer.subscription.deleted", stripeSubscription);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent("payload", "sig", "whsec_12345")).thenReturn(event);

            paymentService.handleWebhook("payload", "sig");

            assertThat(activeSubscription.getStatus()).isEqualTo("CANCELLED");
            verify(subscriptionRepository).save(activeSubscription);
        }
    }

    @Test
    void cancelSubscriptionCancelsStripeAndDatabaseState() throws Exception {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        try (MockedStatic<com.stripe.model.Subscription> mockedStripeSubscription =
                     mockStatic(com.stripe.model.Subscription.class)) {
            com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
            mockedStripeSubscription.when(() -> com.stripe.model.Subscription.retrieve("sub_123"))
                    .thenReturn(stripeSubscription);

            paymentService.cancelSubscription(USER_ID);

            verify(subscriptionRepository).save(activeSubscription);
            assertThat(activeSubscription.getStatus()).isEqualTo("CANCELLED");
            verify(stripeSubscription).update(any(SubscriptionUpdateParams.class));
        }
    }

    @Test
    void cancelSubscriptionStillUpdatesDatabaseWhenStripeCancellationFails() throws Exception {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        StripeException stripeException = mock(StripeException.class);
        when(stripeException.getMessage()).thenReturn("cancel failed");

        try (MockedStatic<com.stripe.model.Subscription> mockedStripeSubscription =
                     mockStatic(com.stripe.model.Subscription.class)) {
            mockedStripeSubscription.when(() -> com.stripe.model.Subscription.retrieve("sub_123"))
                    .thenThrow(stripeException);

            paymentService.cancelSubscription(USER_ID);

            verify(subscriptionRepository).save(activeSubscription);
            assertThat(activeSubscription.getStatus()).isEqualTo("CANCELLED");
        }
    }

    @Test
    void cancelSubscriptionRejectsMissingSubscription() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelSubscription(USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("No subscription found");
    }

    @Test
    void hasFeatureReturnsFalseWhenSubscriptionMissingOrInactive() {
        when(subscriptionRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Subscription.builder().status("PAST_DUE").plan(proPlan).build()));

        assertThat(paymentService.hasFeature(USER_ID, "ADVANCED_ANALYTICS")).isFalse();
        assertThat(paymentService.hasFeature(USER_ID, "ADVANCED_ANALYTICS")).isFalse();
    }

    @Test
    void hasFeatureReturnsPlanFlags() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeSubscription));

        assertThat(paymentService.hasFeature(USER_ID, "ADVANCED_ANALYTICS")).isTrue();
        assertThat(paymentService.hasFeature(USER_ID, "PRIORITY_SUPPORT")).isFalse();
        assertThat(paymentService.hasFeature(USER_ID, "UNKNOWN_FEATURE")).isFalse();
    }

    @Test
    void getPaymentHistoryReturnsRepositoryResults() {
        PaymentRecord record = PaymentRecord.builder().status("SUCCEEDED").build();
        when(paymentRecordRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.singletonList(record));

        List<PaymentRecord> result = paymentService.getPaymentHistory(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SUCCEEDED");
    }

    private Event checkoutEvent(String type, Object payload) {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((com.stripe.model.StripeObject) payload));
        return event;
    }
}
