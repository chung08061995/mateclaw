package vip.mate.llm.account.dto;

import java.math.BigDecimal;

/** Null fields are left unchanged. A supplied API key replaces the encrypted payload. */
public record UpdateProviderAccountRequest(
        String label,
        String externalAccountId,
        String apiKey,
        Integer priority,
        Boolean alertEnabled,
        BigDecimal alertThresholdPercent
) {}
