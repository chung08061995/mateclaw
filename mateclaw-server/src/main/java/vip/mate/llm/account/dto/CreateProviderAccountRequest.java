package vip.mate.llm.account.dto;

import java.math.BigDecimal;

/** Create an API-key account for any provider. OAuth tokens use the OAuth upsert seam. */
public record CreateProviderAccountRequest(
        String providerId,
        String label,
        String externalAccountId,
        String apiKey,
        Integer priority,
        Boolean alertEnabled,
        BigDecimal alertThresholdPercent
) {}
