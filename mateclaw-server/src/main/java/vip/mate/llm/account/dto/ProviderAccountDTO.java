package vip.mate.llm.account.dto;

import vip.mate.llm.account.model.ProviderAccountEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Safe account projection. It contains metadata only, never credential material. */
public record ProviderAccountDTO(
        Long id,
        String providerId,
        String label,
        String externalAccountId,
        String authType,
        boolean credentialConfigured,
        boolean enabled,
        int priority,
        String status,
        String quotaStatus,
        BigDecimal quotaUsedPercent,
        Long quotaResetAt,
        Long quotaUpdatedAt,
        boolean alertEnabled,
        BigDecimal alertThresholdPercent,
        String alertStatus,
        String lastErrorCode,
        String lastErrorMessage,
        Long lastSuccessAt,
        Long lastFailureAt,
        Long lastUsedAt,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        boolean legacyImport,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static ProviderAccountDTO from(ProviderAccountEntity entity) {
        return new ProviderAccountDTO(
                entity.getId(),
                entity.getProviderId(),
                entity.getLabel(),
                entity.getExternalAccountId(),
                entity.getAuthType(),
                entity.getCredentialJson() != null && !entity.getCredentialJson().isBlank(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getPriority() == null ? 100 : entity.getPriority(),
                entity.getStatus(),
                entity.getQuotaStatus(),
                entity.getQuotaUsedPercent(),
                entity.getQuotaResetAt(),
                entity.getQuotaUpdatedAt(),
                Boolean.TRUE.equals(entity.getAlertEnabled()),
                entity.getAlertThresholdPercent(),
                entity.getAlertStatus(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getLastSuccessAt(),
                entity.getLastFailureAt(),
                entity.getLastUsedAt(),
                zero(entity.getPromptTokens()),
                zero(entity.getCompletionTokens()),
                zero(entity.getTotalTokens()),
                Boolean.TRUE.equals(entity.getLegacyImport()),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private static long zero(Long value) {
        return value == null ? 0L : value;
    }
}
