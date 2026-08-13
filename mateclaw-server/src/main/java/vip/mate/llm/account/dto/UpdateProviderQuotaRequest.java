package vip.mate.llm.account.dto;

import java.math.BigDecimal;

/** Quota observation returned by a provider-specific adapter. */
public record UpdateProviderQuotaRequest(
        String quotaStatus,
        BigDecimal usedPercent,
        Long resetAt,
        String errorCode,
        String errorMessage
) {}
