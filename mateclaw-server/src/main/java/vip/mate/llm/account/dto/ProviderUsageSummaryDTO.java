package vip.mate.llm.account.dto;

import java.util.List;

/** Aggregate usage plus safe per-account rows for the Provider Usage screen. */
public record ProviderUsageSummaryDTO(
        int providerCount,
        int accountCount,
        int enabledAccountCount,
        int activeAccountCount,
        int alertCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        List<ProviderAccountDTO> accounts
) {}
