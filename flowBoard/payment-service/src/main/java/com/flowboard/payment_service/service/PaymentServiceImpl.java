package com.flowboard.payment_service.service;

import com.flowboard.payment_service.dto.*;
import com.flowboard.payment_service.entity.*;
import com.flowboard.payment_service.entity.Plan;
import com.flowboard.payment_service.entity.Subscription;
import com.flowboard.payment_service.exception.CustomException;
import com.flowboard.payment_service.repository.*;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PlanRepository         planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    // Frontend URLs for Stripe redirect
    private static final String SUCCESS_URL = "http://localhost:4200/payment/success?session_id={CHECKOUT_SESSION_ID}";
    private static final String CANCEL_URL  = "http://localhost:4200/payment/cancel";

    @PostConstruct
    public void init() {
        configureStripeApiKey();
    }

    @Override
    public List<PlanResponse> getAllPlans() {
        ensureDefaultPlansIfMissing();
        return planRepository.findAll().stream()
                .map(this::toPlanResponse).toList();
    }

    @Override
    public SubscriptionResponse getSubscription(Long userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> createFreeSubscription(userId));
        return toSubResponse(sub);
    }

    @Override
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(
            CreateCheckoutSessionRequest request, Long userId) {

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new CustomException("Plan not found", HttpStatus.NOT_FOUND));

        // Get or create Stripe customer
        Subscription existing = subscriptionRepository.findByUserId(userId).orElse(null);
        String customerId = (existing != null) ? existing.getStripeCustomerId() : null;

        String billingCycle = request.getBillingCycle() == null ? "MONTHLY" : request.getBillingCycle().trim().toUpperCase();

        String priceId = "YEARLY".equals(billingCycle)
                ? plan.getStripePriceIdYearly()
                : plan.getStripePriceIdMonthly();

        try {
            Session session = createStripeSession(plan, userId, billingCycle, customerId, priceId, false);
            log.info("Checkout session created: {} for userId={}", session.getId(), userId);
            return CheckoutSessionResponse.builder()
                    .sessionId(session.getId())
                    .checkoutUrl(session.getUrl())
                    .build();
        } catch (StripeException e) {
            // If configured price ID is missing in this Stripe account, fallback to dynamic price_data.
            if (isMissingPriceError(e) && priceId != null && !priceId.isBlank()) {
                try {
                    log.warn("Configured Stripe price not found ({}). Falling back to dynamic price_data for plan={}",
                            priceId, plan.getName());
                    Session fallbackSession = createStripeSession(plan, userId, billingCycle, customerId, null, true);
                    log.info("Checkout session created with fallback price_data: {} for userId={}",
                            fallbackSession.getId(), userId);
                    return CheckoutSessionResponse.builder()
                            .sessionId(fallbackSession.getId())
                            .checkoutUrl(fallbackSession.getUrl())
                            .build();
                } catch (StripeException fallbackError) {
                    log.error("Stripe checkout fallback failed: {}", fallbackError.getMessage());
                    throw new CustomException("Payment gateway error: " + fallbackError.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

            log.error("Stripe checkout session failed: {}", e.getMessage());
            throw new CustomException("Payment gateway error: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public UpgradeCheckoutResponse createUpgradeCheckoutSession(UpgradeCheckoutRequest request, Long userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("No subscription found", HttpStatus.BAD_REQUEST));

        if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            throw new CustomException("Subscription must be active to upgrade", HttpStatus.BAD_REQUEST);
        }
        if (!"PRO".equalsIgnoreCase(sub.getPlan().getName())) {
            throw new CustomException("Prorated upgrade is only available from Pro to Business", HttpStatus.BAD_REQUEST);
        }

        String targetPlanName = request.getTargetPlanName() == null ? "BUSINESS" : request.getTargetPlanName().trim().toUpperCase();
        if (!"BUSINESS".equals(targetPlanName)) {
            throw new CustomException("Only Business upgrades are supported in this flow", HttpStatus.BAD_REQUEST);
        }

        Plan businessPlan = planRepository.findByName("BUSINESS")
                .orElseThrow(() -> new CustomException("Business plan not found", HttpStatus.NOT_FOUND));

        LocalDate today = LocalDate.now();
        LocalDate periodStart = sub.getCurrentPeriodStart() != null ? sub.getCurrentPeriodStart() : today;
        LocalDate periodEnd = sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd() : today;

        long cycleDays = Math.max(1, ChronoUnit.DAYS.between(periodStart, periodEnd));
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(today, periodEnd));

        BigDecimal businessMonthly = businessPlan.getPriceMonthly() == null ? BigDecimal.ZERO : businessPlan.getPriceMonthly();
        BigDecimal proMonthly = sub.getPlan().getPriceMonthly() == null ? BigDecimal.ZERO : sub.getPlan().getPriceMonthly();
        BigDecimal daysLeftDecimal = BigDecimal.valueOf(daysLeft);
        BigDecimal cycleDaysDecimal = BigDecimal.valueOf(cycleDays);

        BigDecimal businessRemaining = businessMonthly.multiply(daysLeftDecimal)
                .divide(cycleDaysDecimal, 2, RoundingMode.HALF_UP);
        BigDecimal proRemainingCredit = proMonthly.multiply(daysLeftDecimal)
                .divide(cycleDaysDecimal, 2, RoundingMode.HALF_UP);
        BigDecimal amountToCharge = businessRemaining.subtract(proRemainingCredit)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        if (amountToCharge.compareTo(BigDecimal.ZERO) == 0) {
            activatePlanDirectly(sub, businessPlan, "MONTHLY");
            return UpgradeCheckoutResponse.builder()
                    .proratedAmount(amountToCharge)
                    .daysLeft((int) daysLeft)
                    .message("Upgraded to Business without additional charge")
                    .build();
        }

        try {
            Session session = createUpgradePaymentSession(
                    userId,
                    sub.getStripeCustomerId(),
                    sub.getPlan().getName(),
                    businessPlan,
                    amountToCharge,
                    (int) daysLeft
            );
            return UpgradeCheckoutResponse.builder()
                    .sessionId(session.getId())
                    .checkoutUrl(session.getUrl())
                    .proratedAmount(amountToCharge)
                    .daysLeft((int) daysLeft)
                    .message("Prorated checkout created")
                    .build();
        } catch (StripeException e) {
            log.error("Failed to create upgrade checkout session: {}", e.getMessage());
            throw new CustomException("Payment gateway error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public CheckoutSessionStatusResponse confirmCheckoutSession(String sessionId, Long userId) {
        configureStripeApiKey();
        try {
            Session session = Session.retrieve(sessionId);

            if (session == null) {
                return CheckoutSessionStatusResponse.builder()
                        .status("FAILED")
                        .message("Checkout session not found")
                        .build();
            }

            String metadataUserId = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
            if (metadataUserId == null || metadataUserId.isBlank()) {
                metadataUserId = session.getClientReferenceId();
            }
            if (metadataUserId == null || !String.valueOf(userId).equals(metadataUserId)) {
                return CheckoutSessionStatusResponse.builder()
                        .status("FAILED")
                        .message("Checkout session does not belong to this user")
                        .build();
            }

            String paymentStatus = session.getPaymentStatus();
            String status = session.getStatus();
            String planName = extractPlanName(session);
            String billingCycle = session.getMetadata() != null
                    ? session.getMetadata().getOrDefault("billingCycle", "MONTHLY")
                    : "MONTHLY";

            if ("complete".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(paymentStatus)) {
                if (isProratedUpgradeSession(session)) {
                    activateProratedUpgrade(session);
                    planName = "BUSINESS";
                } else {
                    activateSubscription(session);
                }
                return CheckoutSessionStatusResponse.builder()
                        .status("ACTIVE")
                        .planName(planName)
                        .billingCycle(billingCycle)
                        .message("Subscription activated")
                        .build();
            }

            return CheckoutSessionStatusResponse.builder()
                    .status("PENDING")
                    .planName(planName)
                    .billingCycle(billingCycle)
                    .message("Checkout is not completed yet")
                    .build();
        } catch (StripeException e) {
            log.error("Failed to confirm checkout session {}: {}", sessionId, e.getMessage());
            return CheckoutSessionStatusResponse.builder()
                    .status("FAILED")
                    .message("Payment gateway error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected confirm error for session {}: {}", sessionId, e.getMessage(), e);
            return CheckoutSessionStatusResponse.builder()
                    .status("FAILED")
                    .message("Unable to confirm payment right now")
                    .build();
        }
    }

    private SessionCreateParams.LineItem buildLineItem(Plan plan, String billingCycle, String priceId) {
        if (priceId != null && !priceId.isBlank()) {
            if (!priceId.startsWith("price_")) {
                throw new CustomException(
                        "Invalid Stripe price ID for plan " + plan.getName() +
                                ". Expected value starting with 'price_' but got '" + priceId + "'",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build();
        }

        BigDecimal amount = "YEARLY".equals(billingCycle) ? plan.getPriceYearly() : plan.getPriceMonthly();
        if (amount == null) {
            throw new CustomException(
                    "Plan amount is missing for " + plan.getName() + " (" + billingCycle + ")",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        long unitAmount = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        SessionCreateParams.LineItem.PriceData.Recurring.Interval interval =
                "YEARLY".equals(billingCycle)
                        ? SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR
                        : SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("usd")
                        .setUnitAmount(unitAmount)
                        .setRecurring(
                                SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                        .setInterval(interval)
                                        .build()
                        )
                        .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("FlowBoard " + plan.getDisplayName())
                                        .build()
                        )
                        .build();

        return SessionCreateParams.LineItem.builder()
                .setPriceData(priceData)
                .setQuantity(1L)
                .build();
    }

    private Session createStripeSession(
            Plan plan,
            Long userId,
            String billingCycle,
            String customerId,
            String priceId,
            boolean forceDynamicPriceData
    ) throws StripeException {
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(SUCCESS_URL)
                .setCancelUrl(CANCEL_URL)
                .setClientReferenceId(String.valueOf(userId))
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("planId", String.valueOf(plan.getId()))
                .putMetadata("billingCycle", billingCycle);

        SessionCreateParams.SubscriptionData subscriptionData = SessionCreateParams.SubscriptionData.builder()
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("planId", String.valueOf(plan.getId()))
                .putMetadata("billingCycle", billingCycle)
                .build();
        params.setSubscriptionData(subscriptionData);

        String effectivePriceId = forceDynamicPriceData ? null : priceId;
        params.addLineItem(buildLineItem(plan, billingCycle, effectivePriceId));

        if (customerId != null) {
            params.setCustomer(customerId);
        }

        return Session.create(params.build());
    }

    private Session createUpgradePaymentSession(
            Long userId,
            String customerId,
            String fromPlan,
            Plan toPlan,
            BigDecimal amountToCharge,
            Integer daysLeft
    ) throws StripeException {
        long unitAmount = amountToCharge.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(SUCCESS_URL)
                .setCancelUrl(CANCEL_URL)
                .setClientReferenceId(String.valueOf(userId))
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("upgradeType", "PRO_TO_BUSINESS_PRORATED")
                .putMetadata("fromPlan", fromPlan)
                .putMetadata("toPlan", toPlan.getName())
                .putMetadata("daysLeft", String.valueOf(daysLeft))
                .putMetadata("proratedAmount", amountToCharge.toPlainString());

        params.addLineItem(
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd")
                                        .setUnitAmount(unitAmount)
                                        .setProductData(
                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                        .setName("FlowBoard Business upgrade (prorated)")
                                                        .build()
                                        )
                                        .build()
                        )
                        .build()
        );

        if (customerId != null && !customerId.isBlank()) {
            params.setCustomer(customerId);
        }

        return Session.create(params.build());
    }

    private boolean isProratedUpgradeSession(Session session) {
        return session.getMetadata() != null
                && "PRO_TO_BUSINESS_PRORATED".equals(session.getMetadata().get("upgradeType"));
    }

    private boolean isMissingPriceError(StripeException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("No such price");
    }

    private void configureStripeApiKey() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new CustomException("Stripe secret key is missing. Set STRIPE_SECRET_KEY.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!stripeSecretKey.startsWith("sk_")) {
            throw new CustomException(
                    "Invalid Stripe secret key type. Use a Secret key starting with 'sk_' (not pk_ or rk_).",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String stripeSignature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, stripeSignature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature");
            throw new CustomException("Invalid webhook signature", HttpStatus.BAD_REQUEST);
        }

        log.info("Stripe webhook received: type={}", event.getType());

        switch (event.getType()) {

            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject().orElseThrow();
                if (isProratedUpgradeSession(session)) {
                    activateProratedUpgrade(session);
                } else {
                    activateSubscription(session);
                }
            }

            case "invoice.payment_succeeded", "invoice_payment.succeeded", "invoice_payment.paid" -> {
                Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject().orElseThrow();
                recordPayment(invoice, "SUCCEEDED");
                renewSubscription(invoice);
            }

            case "invoice.payment_failed", "invoice_payment.failed" -> {
                Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject().orElseThrow();
                recordPayment(invoice, "FAILED");
                markPastDue(invoice.getCustomer());
            }

            case "customer.subscription.deleted" -> {
                com.stripe.model.Subscription stripeSub =
                        (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                                .getObject().orElseThrow();
                cancelByStripeId(stripeSub.getId());
            }

            default -> log.debug("Unhandled Stripe event: {}", event.getType());
        }
    }

    @Override
    @Transactional
    public void cancelSubscription(Long userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("No subscription found", HttpStatus.NOT_FOUND));

        if (sub.getStripeSubscriptionId() != null) {
            try {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(sub.getStripeSubscriptionId());
                // Cancel at period end — user keeps access until billing period ends
                stripeSub.update(SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true).build());
            } catch (StripeException e) {
                log.error("Failed to cancel Stripe subscription: {}", e.getMessage());
            }
        }

        sub.setStatus("CANCELLED");
        sub.setCancelledAt(java.time.LocalDateTime.now());
        subscriptionRepository.save(sub);
        log.info("Subscription cancelled for userId={}", userId);
    }

    @Override
    public boolean hasFeature(Long userId, String feature) {
        Subscription sub = subscriptionRepository.findByUserId(userId).orElse(null);
        if (sub == null || !"ACTIVE".equals(sub.getStatus())) return false;
        Plan plan = sub.getPlan();
        return switch (feature) {
            case "ADVANCED_ANALYTICS" -> plan.isHasAdvancedAnalytics();
            case "PRIORITY_SUPPORT"   -> plan.isHasPrioritySupport();
            case "CUSTOM_FIELDS"      -> plan.isHasCustomFields();
            case "AUTOMATION"         -> plan.isHasAutomation();
            default -> false;
        };
    }

    @Override
    public List<PaymentRecord> getPaymentHistory(Long userId) {
        return paymentRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void activateProratedUpgrade(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String userIdText = metadata != null ? metadata.get("userId") : null;
        if (userIdText == null || userIdText.isBlank()) userIdText = session.getClientReferenceId();
        if (userIdText == null || userIdText.isBlank()) {
            throw new CustomException("Missing userId in upgrade session", HttpStatus.BAD_REQUEST);
        }

        String toPlanName = metadata != null ? metadata.get("toPlan") : null;
        if (toPlanName == null || toPlanName.isBlank()) {
            throw new CustomException("Missing target plan in upgrade session", HttpStatus.BAD_REQUEST);
        }

        Long userId = Long.parseLong(userIdText);
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("Subscription not found", HttpStatus.NOT_FOUND));
        Plan toPlan = planRepository.findByName(toPlanName.toUpperCase())
                .orElseThrow(() -> new CustomException("Target plan not found", HttpStatus.NOT_FOUND));
        activatePlanDirectly(sub, toPlan, "MONTHLY");
    }

    private void activatePlanDirectly(Subscription sub, Plan plan, String cycle) {
        LocalDate now = LocalDate.now();
        sub.setPlan(plan);
        sub.setStatus("ACTIVE");
        sub.setBillingCycle(cycle);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd("YEARLY".equals(cycle) ? now.plusYears(1) : now.plusMonths(1));
        sub.setCancelledAt(null);
        subscriptionRepository.save(sub);
    }

    private void activateSubscription(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String userIdText = metadata != null ? metadata.get("userId") : null;
        if (userIdText == null || userIdText.isBlank()) userIdText = session.getClientReferenceId();
        if (userIdText == null || userIdText.isBlank()) {
            throw new CustomException("Missing userId in Stripe session metadata", HttpStatus.BAD_REQUEST);
        }

        String planIdText = metadata != null ? metadata.get("planId") : null;
        if (planIdText == null || planIdText.isBlank()) {
            throw new CustomException("Missing planId in Stripe session metadata", HttpStatus.BAD_REQUEST);
        }

        Long userId = Long.parseLong(userIdText);
        Long planId = Long.parseLong(planIdText);
        String cycle = metadata != null ? metadata.getOrDefault("billingCycle", "MONTHLY") : "MONTHLY";

        Plan plan = planRepository.findById(planId).orElseThrow();

        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElse(Subscription.builder().userId(userId).build());

        // Idempotent: if already activated for this Stripe subscription, no-op
        if (sub.getStripeSubscriptionId() != null
                && session.getSubscription() != null
                && sub.getStripeSubscriptionId().equals(session.getSubscription())
                && "ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            return;
        }

        LocalDate now = LocalDate.now();
        sub.setPlan(plan);
        sub.setStatus("ACTIVE");
        sub.setBillingCycle(cycle);
        sub.setStripeCustomerId(session.getCustomer());
        sub.setStripeSubscriptionId(session.getSubscription());
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd("YEARLY".equals(cycle) ? now.plusYears(1) : now.plusMonths(1));

        subscriptionRepository.save(sub);
        log.info("Subscription activated: userId={} plan={}", userId, plan.getName());
    }

    private String extractPlanName(Session session) {
        try {
            if (session.getMetadata() == null) return null;
            String planId = session.getMetadata().get("planId");
            if (planId == null) return null;
            return planRepository.findById(Long.parseLong(planId))
                    .map(Plan::getName)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renewSubscription(Invoice invoice) {
        subscriptionRepository.findByStripeCustomerId(invoice.getCustomer())
                .ifPresent(sub -> {
                    sub.setStatus("ACTIVE");
                    LocalDate now = LocalDate.now();
                    sub.setCurrentPeriodStart(now);
                    sub.setCurrentPeriodEnd(
                            "YEARLY".equals(sub.getBillingCycle())
                                    ? now.plusYears(1)
                                    : now.plusMonths(1));
                    subscriptionRepository.save(sub);
                });
    }

    private void recordPayment(Invoice invoice, String status) {
        subscriptionRepository.findByStripeCustomerId(invoice.getCustomer())
                .ifPresent(sub -> {
                    PaymentRecord record = PaymentRecord.builder()
                            .userId(sub.getUserId())
                            .stripePaymentIntentId(invoice.getPaymentIntent())
                            .amount(BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100)))
                            .currency(invoice.getCurrency().toUpperCase())
                            .status(status)
                            .description("Invoice " + invoice.getId())
                            .build();
                    paymentRecordRepository.save(record);
                });
    }

    private void markPastDue(String customerId) {
        subscriptionRepository.findByStripeCustomerId(customerId)
                .ifPresent(sub -> {
                    sub.setStatus("PAST_DUE");
                    subscriptionRepository.save(sub);
                });
    }

    private void cancelByStripeId(String stripeSubId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubId)
                .ifPresent(sub -> {
                    sub.setStatus("CANCELLED");
                    subscriptionRepository.save(sub);
                });
    }

    private Subscription createFreeSubscription(Long userId) {
        ensureDefaultPlansIfMissing();
        Plan freePlan = planRepository.findByName("FREE")
                .orElseThrow(() -> new CustomException(
                        "FREE plan not seeded", HttpStatus.INTERNAL_SERVER_ERROR));
        Subscription sub = Subscription.builder()
                .userId(userId).plan(freePlan).status("ACTIVE")
                .billingCycle("MONTHLY")
                .currentPeriodStart(LocalDate.now())
                .currentPeriodEnd(LocalDate.now().plusYears(100))
                .build();
        return subscriptionRepository.save(sub);
    }

    private PlanResponse toPlanResponse(Plan p) {
        return PlanResponse.builder()
                .id(p.getId()).name(p.getName()).displayName(p.getDisplayName())
                .description(p.getDescription()).priceMonthly(p.getPriceMonthly())
                .priceYearly(p.getPriceYearly()).maxWorkspaces(p.getMaxWorkspaces())
                .maxBoardsPerWorkspace(p.getMaxBoardsPerWorkspace())
                .maxMembersPerWorkspace(p.getMaxMembersPerWorkspace())
                .hasAdvancedAnalytics(p.isHasAdvancedAnalytics())
                .hasPrioritySupport(p.isHasPrioritySupport())
                .hasCustomFields(p.isHasCustomFields())
                .hasAutomation(p.isHasAutomation())
                .build();
    }

    private SubscriptionResponse toSubResponse(Subscription s) {
        Plan p = s.getPlan();
        return SubscriptionResponse.builder()
                .id(s.getId()).userId(s.getUserId())
                .planName(p.getName()).planDisplayName(p.getDisplayName())
                .status(s.getStatus()).billingCycle(s.getBillingCycle())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .hasAdvancedAnalytics(p.isHasAdvancedAnalytics())
                .hasPrioritySupport(p.isHasPrioritySupport())
                .hasCustomFields(p.isHasCustomFields())
                .hasAutomation(p.isHasAutomation())
                .maxWorkspaces(p.getMaxWorkspaces())
                .maxBoardsPerWorkspace(p.getMaxBoardsPerWorkspace())
                .build();
    }

    private void ensureDefaultPlansIfMissing() {
        if (planRepository.count() > 0) return;

        Plan free = Plan.builder()
                .name("FREE")
                .displayName("Free")
                .description("Get started for free")
                .priceMonthly(BigDecimal.ZERO)
                .priceYearly(BigDecimal.ZERO)
                .maxWorkspaces(3)
                .maxBoardsPerWorkspace(5)
                .maxMembersPerWorkspace(10)
                .hasAdvancedAnalytics(false)
                .hasPrioritySupport(false)
                .hasCustomFields(false)
                .hasAutomation(false)
                .build();

        Plan pro = Plan.builder()
                .name("PRO")
                .displayName("Pro")
                .description("For growing teams")
                .priceMonthly(new BigDecimal("9.99"))
                .priceYearly(new BigDecimal("99.00"))
                .maxWorkspaces(-1)
                .maxBoardsPerWorkspace(-1)
                .maxMembersPerWorkspace(50)
                .hasAdvancedAnalytics(true)
                .hasPrioritySupport(false)
                .hasCustomFields(true)
                .hasAutomation(false)
                .build();

        Plan business = Plan.builder()
                .name("BUSINESS")
                .displayName("Business")
                .description("For enterprises")
                .priceMonthly(new BigDecimal("29.99"))
                .priceYearly(new BigDecimal("299.00"))
                .maxWorkspaces(-1)
                .maxBoardsPerWorkspace(-1)
                .maxMembersPerWorkspace(-1)
                .hasAdvancedAnalytics(true)
                .hasPrioritySupport(true)
                .hasCustomFields(true)
                .hasAutomation(true)
                .build();

        planRepository.saveAll(List.of(free, pro, business));
        log.warn("Plans table was empty. Re-seeded FREE/PRO/BUSINESS defaults.");
    }
}
