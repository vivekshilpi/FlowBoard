package com.flowboard.payment_service.config;

import com.flowboard.payment_service.entity.Plan;
import com.flowboard.payment_service.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PlanRepository planRepository;
    @Value("${stripe.prices.pro.monthly:price_1TODqvANErF8WTmGRzRIjKTG}")
    private String proMonthlyPriceId;
    @Value("${stripe.prices.pro.yearly:price_1TODuYANErF8WTmG9Ce6IDKb}")
    private String proYearlyPriceId;
    @Value("${stripe.prices.business.monthly:}")
    private String businessMonthlyPriceId;
    @Value("${stripe.prices.business.yearly:price_1TODtdANErF8WTmG8sLj6zxC}")
    private String businessYearlyPriceId;

    @Override
    public void run(String... args) {
        upsertPlan(
                "FREE", "Free", "Get started for free",
                BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                3, 5, 10, false, false, false, false
        );

        upsertPlan(
                "PRO", "Pro", "For growing teams",
                new BigDecimal("9.99"), new BigDecimal("99.00"),
                proMonthlyPriceId, proYearlyPriceId,
                -1, -1, 50, true, false, true, false
        );

        upsertPlan(
                "BUSINESS", "Business", "For enterprises",
                new BigDecimal("29.99"), new BigDecimal("299.00"),
                businessMonthlyPriceId, businessYearlyPriceId,
                -1, -1, -1, true, true, true, true
        );

        log.info("Plans seeded: FREE, PRO, BUSINESS");
    }

    private void upsertPlan(
            String name,
            String displayName,
            String description,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice,
            String stripePriceIdMonthly,
            String stripePriceIdYearly,
            int maxWorkspaces,
            int maxBoardsPerWorkspace,
            int maxMembersPerWorkspace,
            boolean hasAdvancedAnalytics,
            boolean hasPrioritySupport,
            boolean hasCustomFields,
            boolean hasAutomation
    ) {
        Plan plan = planRepository.findByName(name).orElseGet(Plan::new);
        plan.setName(name);
        plan.setDisplayName(displayName);
        plan.setDescription(description);
        plan.setPriceMonthly(monthlyPrice);
        plan.setPriceYearly(yearlyPrice);
        plan.setStripePriceIdMonthly(normalizePriceId(stripePriceIdMonthly));
        plan.setStripePriceIdYearly(normalizePriceId(stripePriceIdYearly));
        plan.setMaxWorkspaces(maxWorkspaces);
        plan.setMaxBoardsPerWorkspace(maxBoardsPerWorkspace);
        plan.setMaxMembersPerWorkspace(maxMembersPerWorkspace);
        plan.setHasAdvancedAnalytics(hasAdvancedAnalytics);
        plan.setHasPrioritySupport(hasPrioritySupport);
        plan.setHasCustomFields(hasCustomFields);
        plan.setHasAutomation(hasAutomation);
        planRepository.save(plan);
    }

    private String normalizePriceId(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
