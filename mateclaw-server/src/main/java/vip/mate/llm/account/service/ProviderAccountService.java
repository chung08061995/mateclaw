package vip.mate.llm.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import vip.mate.exception.MateClawException;
import vip.mate.llm.account.dto.CreateProviderAccountRequest;
import vip.mate.llm.account.dto.ProviderAccountDTO;
import vip.mate.llm.account.dto.ProviderUsageSummaryDTO;
import vip.mate.llm.account.dto.UpdateProviderAccountRequest;
import vip.mate.llm.account.dto.UpdateProviderQuotaRequest;
import vip.mate.llm.account.model.ProviderAccountCredentials;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.model.ProviderAccountStatus;
import vip.mate.llm.account.model.ProviderQuotaStatus;
import vip.mate.llm.account.repository.ProviderAccountMapper;
import vip.mate.llm.model.ModelProviderEntity;
import vip.mate.llm.repository.ModelProviderMapper;
import vip.mate.system.service.SettingCrypto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistence seam for multiple credentials per logical model provider.
 * Credential encryption, legacy import, lifecycle and counters are kept here so
 * routing code only needs the small list/decrypt/record interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderAccountService {
    private static final String AUTH_API_KEY = "api_key";
    private static final String AUTH_OAUTH = "oauth";
    private static final BigDecimal DEFAULT_ALERT_THRESHOLD = new BigDecimal("80.00");
    private static final Pattern RESET_DURATION = Pattern.compile(
            "(?:(\\d+(?:\\.\\d+)?)h)?(?:(\\d+(?:\\.\\d+)?)m)?(?:(\\d+(?:\\.\\d+)?)s)?",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> QUOTA_STATUSES = Set.of(
            ProviderQuotaStatus.UNKNOWN,
            ProviderQuotaStatus.OK,
            ProviderQuotaStatus.WARNING,
            ProviderQuotaStatus.RATE_LIMITED,
            ProviderQuotaStatus.EXHAUSTED);

    private final ProviderAccountMapper accountMapper;
    private final ModelProviderMapper legacyProviderMapper;
    private final SettingCrypto settingCrypto;
    private final ObjectMapper objectMapper;

    /** Ordered, currently usable accounts for one provider. */
    @Transactional
    public List<ProviderAccountEntity> listUsableAccounts(String providerId) {
        requireText(providerId, "providerId");
        importLegacyProvider(providerId);
        releaseExpiredQuota(providerId);
        return accountMapper.selectList(new LambdaQueryWrapper<ProviderAccountEntity>()
                        .eq(ProviderAccountEntity::getProviderId, providerId)
                        .eq(ProviderAccountEntity::getEnabled, true)
                        .in(ProviderAccountEntity::getStatus,
                                ProviderAccountStatus.AVAILABLE,
                                ProviderAccountStatus.ACTIVE)
                        .notIn(ProviderAccountEntity::getQuotaStatus,
                                ProviderQuotaStatus.RATE_LIMITED,
                                ProviderQuotaStatus.EXHAUSTED)
                        .orderByAsc(ProviderAccountEntity::getPriority)
                        .orderByAsc(ProviderAccountEntity::getId))
                .stream()
                .filter(row -> StringUtils.hasText(row.getCredentialJson()))
                .toList();
    }

    /** True once a provider owns a credential, even if every account is cooling down. */
    @Transactional
    public boolean hasConfiguredAccounts(String providerId) {
        requireText(providerId, "providerId");
        importLegacyProvider(providerId);
        Long count = accountMapper.selectCount(
                new LambdaQueryWrapper<ProviderAccountEntity>()
                        .eq(ProviderAccountEntity::getProviderId, providerId)
                        .isNotNull(ProviderAccountEntity::getCredentialJson));
        return count != null && count > 0;
    }

    /** Decrypt only at the model-construction seam. Never return this from a controller. */
    public ProviderAccountCredentials getDecryptedCredentials(Long accountId) {
        ProviderAccountEntity entity = getRequired(accountId);
        try {
            String json = settingCrypto.decrypt(entity.getCredentialJson());
            CredentialPayload payload = objectMapper.readValue(json, CredentialPayload.class);
            return new ProviderAccountCredentials(
                    payload.apiKey(),
                    payload.accessToken(),
                    payload.refreshToken(),
                    entity.getExternalAccountId(),
                    payload.expiresAt());
        } catch (Exception e) {
            throw new MateClawException("Stored credentials for provider account " + accountId
                    + " cannot be decrypted or parsed");
        }
    }

    /** Atomically record a successful request and cumulative MateClaw token use. */
    public void recordSuccess(Long accountId, long prompt, long completion, long total) {
        if (prompt < 0 || completion < 0 || total < 0) {
            throw new MateClawException("Token counters cannot be negative");
        }
        if (accountMapper.recordSuccess(accountId, prompt, completion, total,
                System.currentTimeMillis()) == 0) {
            throw notFound(accountId);
        }
    }

    /** Classify a failed request into cooldown, exhaustion or a general account error. */
    public void recordFailure(Long accountId, String errorCode, String message, Long resetAtMs) {
        ProviderAccountEntity entity = getRequired(accountId);
        String signal = ((errorCode == null ? "" : errorCode) + " "
                + (message == null ? "" : message)).toLowerCase(Locale.ROOT);
        if (signal.contains("insufficient_quota") || signal.contains("quota_exhaust")
                || signal.contains("usage_limit") || signal.contains("credit balance")) {
            entity.setStatus(ProviderAccountStatus.EXHAUSTED);
            entity.setQuotaStatus(ProviderQuotaStatus.EXHAUSTED);
            entity.setAlertStatus("TRIGGERED");
        } else if (signal.contains("rate_limit") || signal.contains("rate limit")
                || signal.contains("429")) {
            entity.setStatus(ProviderAccountStatus.COOLDOWN);
            entity.setQuotaStatus(ProviderQuotaStatus.RATE_LIMITED);
            entity.setAlertStatus("TRIGGERED");
            if (resetAtMs == null) resetAtMs = System.currentTimeMillis() + 60_000;
        } else if (signal.contains("auth_error") || signal.contains("unauthorized")
                || signal.contains("invalid api key") || signal.contains("token_revoked")) {
            entity.setStatus(ProviderAccountStatus.ERROR);
        } else {
            entity.setStatus(ProviderAccountStatus.COOLDOWN);
            if (resetAtMs == null) resetAtMs = System.currentTimeMillis() + 60_000;
        }
        entity.setQuotaResetAt(resetAtMs);
        entity.setLastErrorCode(truncate(errorCode, 128));
        entity.setLastErrorMessage(truncate(message, 4000));
        entity.setLastFailureAt(System.currentTimeMillis());
        accountMapper.updateById(entity);
    }

    /** Mark the selected credential active and release the previous active row. */
    @Transactional
    public void markActive(Long accountId) {
        ProviderAccountEntity entity = getRequired(accountId);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new MateClawException("Disabled provider accounts cannot be activated");
        }
        if (isQuotaBlocked(entity)) {
            entity.setLastUsedAt(System.currentTimeMillis());
            accountMapper.updateById(entity);
            return;
        }
        accountMapper.update(null, new LambdaUpdateWrapper<ProviderAccountEntity>()
                .eq(ProviderAccountEntity::getProviderId, entity.getProviderId())
                .eq(ProviderAccountEntity::getStatus, ProviderAccountStatus.ACTIVE)
                .set(ProviderAccountEntity::getStatus, ProviderAccountStatus.AVAILABLE));
        entity.setStatus(ProviderAccountStatus.ACTIVE);
        entity.setLastUsedAt(System.currentTimeMillis());
        accountMapper.updateById(entity);
    }

    /** Generic OAuth credential upsert used by OpenAI, Claude and Gemini adapters. */
    @Transactional
    public ProviderAccountEntity upsertOAuthAccount(String providerId,
                                                     String label,
                                                     String externalAccountId,
                                                     String accessToken,
                                                     String refreshToken,
                                                     Long expiresAt) {
        requireText(providerId, "providerId");
        requireText(label, "label");
        requireText(accessToken, "accessToken");

        LambdaQueryWrapper<ProviderAccountEntity> query = new LambdaQueryWrapper<ProviderAccountEntity>()
                .eq(ProviderAccountEntity::getProviderId, providerId)
                .eq(ProviderAccountEntity::getAuthType, AUTH_OAUTH);
        if (StringUtils.hasText(externalAccountId)) {
            query.eq(ProviderAccountEntity::getExternalAccountId, externalAccountId.trim());
        } else {
            query.eq(ProviderAccountEntity::getLabel, label.trim());
        }
        ProviderAccountEntity entity = accountMapper.selectOne(query.last("LIMIT 1"));
        boolean created = entity == null;
        if (created) {
            entity = newBaseEntity(providerId, label, AUTH_OAUTH, nextPriority(providerId));
        }
        CredentialPayload previous = created ? null : readCredentialPayload(entity);
        String effectiveRefreshToken = StringUtils.hasText(refreshToken)
                ? refreshToken
                : previous == null ? null : previous.refreshToken();
        entity.setLabel(label.trim());
        entity.setExternalAccountId(trimToNull(externalAccountId));
        entity.setCredentialJson(encrypt(new CredentialPayload(
                null, accessToken, effectiveRefreshToken, expiresAt)));
        entity.setEnabled(true);
        entity.setStatus(ProviderAccountStatus.AVAILABLE);
        entity.setQuotaStatus(ProviderQuotaStatus.UNKNOWN);
        entity.setLastErrorCode(null);
        entity.setLastErrorMessage(null);
        if (created) {
            accountMapper.insert(entity);
        } else {
            accountMapper.updateById(entity);
        }
        return entity;
    }

    /** Safe account metadata lookup for an OAuth refresh adapter. */
    public ProviderAccountEntity getAccount(Long accountId) {
        return getRequired(accountId);
    }

    /**
     * Replace a pooled OAuth access token after refresh. Providers commonly omit
     * refresh_token on a refresh response, so blank preserves the stored value.
     */
    public void refreshOAuthAccountCredentials(Long accountId, String accessToken,
                                               String refreshToken, Long expiresAt) {
        requireText(accessToken, "accessToken");
        ProviderAccountEntity entity = getRequired(accountId);
        if (!AUTH_OAUTH.equals(entity.getAuthType())) {
            throw new MateClawException("Provider account is not OAuth: " + accountId);
        }
        CredentialPayload previous = readCredentialPayload(entity);
        String effectiveRefreshToken = StringUtils.hasText(refreshToken)
                ? refreshToken : previous.refreshToken();
        entity.setCredentialJson(encrypt(new CredentialPayload(
                null, accessToken, effectiveRefreshToken, expiresAt)));
        entity.setStatus(ProviderAccountStatus.AVAILABLE);
        entity.setLastErrorCode(null);
        entity.setLastErrorMessage(null);
        accountMapper.updateById(entity);
    }

    @Transactional
    public List<ProviderAccountDTO> listAccounts(String providerId) {
        if (StringUtils.hasText(providerId)) {
            importLegacyProvider(providerId.trim());
            releaseExpiredQuota(providerId.trim());
        } else {
            importAllLegacyProviders();
            releaseExpiredQuota(null);
        }
        LambdaQueryWrapper<ProviderAccountEntity> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(providerId)) {
            query.eq(ProviderAccountEntity::getProviderId, providerId.trim());
        }
        query.orderByAsc(ProviderAccountEntity::getProviderId)
                .orderByAsc(ProviderAccountEntity::getPriority)
                .orderByAsc(ProviderAccountEntity::getId);
        return accountMapper.selectList(query).stream().map(ProviderAccountDTO::from).toList();
    }

    @Transactional
    public ProviderAccountDTO createApiKeyAccount(CreateProviderAccountRequest request) {
        if (request == null) throw new MateClawException("Request body is required");
        requireText(request.providerId(), "providerId");
        requireText(request.label(), "label");
        requireText(request.apiKey(), "apiKey");
        validatePercent(request.alertThresholdPercent(), "alertThresholdPercent");

        ProviderAccountEntity entity = newBaseEntity(
                request.providerId().trim(), request.label().trim(), AUTH_API_KEY,
                request.priority() == null ? nextPriority(request.providerId().trim()) : request.priority());
        entity.setExternalAccountId(trimToNull(request.externalAccountId()));
        entity.setCredentialJson(encrypt(new CredentialPayload(request.apiKey().trim(), null, null, null)));
        entity.setAlertEnabled(request.alertEnabled() == null || request.alertEnabled());
        entity.setAlertThresholdPercent(request.alertThresholdPercent() == null
                ? DEFAULT_ALERT_THRESHOLD : scale(request.alertThresholdPercent()));
        accountMapper.insert(entity);
        return ProviderAccountDTO.from(entity);
    }

    public ProviderAccountDTO updateAccount(Long accountId, UpdateProviderAccountRequest request) {
        if (request == null) throw new MateClawException("Request body is required");
        ProviderAccountEntity entity = getRequired(accountId);
        validatePercent(request.alertThresholdPercent(), "alertThresholdPercent");
        if (request.label() != null) {
            requireText(request.label(), "label");
            entity.setLabel(request.label().trim());
        }
        if (request.externalAccountId() != null) {
            entity.setExternalAccountId(trimToNull(request.externalAccountId()));
        }
        if (request.apiKey() != null) {
            if (!AUTH_API_KEY.equals(entity.getAuthType())) {
                throw new MateClawException("OAuth credentials must be updated by the OAuth login flow");
            }
            requireText(request.apiKey(), "apiKey");
            entity.setCredentialJson(encrypt(new CredentialPayload(request.apiKey().trim(), null, null, null)));
            entity.setStatus(ProviderAccountStatus.AVAILABLE);
            entity.setLastErrorCode(null);
            entity.setLastErrorMessage(null);
        }
        if (request.priority() != null) entity.setPriority(request.priority());
        if (request.alertEnabled() != null) entity.setAlertEnabled(request.alertEnabled());
        if (request.alertThresholdPercent() != null) {
            entity.setAlertThresholdPercent(scale(request.alertThresholdPercent()));
        }
        accountMapper.updateById(entity);
        return ProviderAccountDTO.from(entity);
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        ProviderAccountEntity account = getRequired(accountId);
        clearOwnedLegacyCredential(account);
        if (accountMapper.deleteById(accountId) == 0) throw notFound(accountId);
    }

    public ProviderAccountDTO setEnabled(Long accountId, boolean enabled) {
        ProviderAccountEntity entity = getRequired(accountId);
        entity.setEnabled(enabled);
        if (!enabled) {
            entity.setStatus(ProviderAccountStatus.DISABLED);
        } else if (isQuotaBlocked(entity) && isFuture(entity.getQuotaResetAt())) {
            entity.setStatus(ProviderQuotaStatus.EXHAUSTED.equals(entity.getQuotaStatus())
                    ? ProviderAccountStatus.EXHAUSTED : ProviderAccountStatus.COOLDOWN);
        } else {
            entity.setStatus(ProviderAccountStatus.AVAILABLE);
        }
        accountMapper.updateById(entity);
        return ProviderAccountDTO.from(entity);
    }

    @Transactional
    public List<ProviderAccountDTO> reorder(String providerId, List<Long> orderedIds) {
        requireText(providerId, "providerId");
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new MateClawException("accountIds is required");
        }
        List<ProviderAccountEntity> rows = accountMapper.selectList(
                new LambdaQueryWrapper<ProviderAccountEntity>()
                        .eq(ProviderAccountEntity::getProviderId, providerId));
        Set<Long> expected = rows.stream().map(ProviderAccountEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> supplied = new HashSet<>(orderedIds);
        if (orderedIds.size() != supplied.size() || !expected.equals(supplied)) {
            throw new MateClawException("accountIds must contain every account for provider " + providerId
                    + " exactly once");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            ProviderAccountEntity update = new ProviderAccountEntity();
            update.setId(orderedIds.get(i));
            update.setPriority(i + 1);
            accountMapper.updateById(update);
        }
        return listAccounts(providerId);
    }

    public ProviderAccountDTO updateQuotaStatus(Long accountId, UpdateProviderQuotaRequest request) {
        if (request == null) throw new MateClawException("Request body is required");
        ProviderAccountEntity entity = getRequired(accountId);
        String quotaStatus = normalizeQuotaStatus(request.quotaStatus());
        validatePercent(request.usedPercent(), "usedPercent");
        entity.setQuotaStatus(quotaStatus);
        entity.setQuotaUsedPercent(request.usedPercent() == null ? null : scale(request.usedPercent()));
        entity.setQuotaResetAt(request.resetAt());
        entity.setQuotaUpdatedAt(System.currentTimeMillis());
        entity.setLastErrorCode(truncate(request.errorCode(), 128));
        entity.setLastErrorMessage(truncate(request.errorMessage(), 4000));

        boolean warning = ProviderQuotaStatus.WARNING.equals(quotaStatus)
                || (entity.getQuotaUsedPercent() != null
                    && entity.getAlertThresholdPercent() != null
                    && entity.getQuotaUsedPercent().compareTo(entity.getAlertThresholdPercent()) >= 0);
        boolean blocked = ProviderQuotaStatus.RATE_LIMITED.equals(quotaStatus)
                || ProviderQuotaStatus.EXHAUSTED.equals(quotaStatus);
        entity.setAlertStatus(Boolean.TRUE.equals(entity.getAlertEnabled()) && (warning || blocked)
                ? (blocked ? "TRIGGERED" : "WARNING") : "NONE");
        if (ProviderQuotaStatus.RATE_LIMITED.equals(quotaStatus)) {
            entity.setStatus(ProviderAccountStatus.COOLDOWN);
        } else if (ProviderQuotaStatus.EXHAUSTED.equals(quotaStatus)) {
            entity.setStatus(ProviderAccountStatus.EXHAUSTED);
        } else if (Boolean.TRUE.equals(entity.getEnabled())) {
            entity.setStatus(ProviderAccountStatus.AVAILABLE);
        }
        accountMapper.updateById(entity);
        return ProviderAccountDTO.from(entity);
    }

    /**
     * Capture provider-reported quota metadata without assuming every vendor
     * exposes the same headers. Unknown headers are ignored and never replace
     * MateClaw's own token counters.
     */
    public void observeQuotaHeaders(Long accountId, HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) return;
        BigDecimal usedPercent = firstDecimal(headers,
                "x-codex-primary-used-percent",
                "x-openai-quota-used-percent",
                "x-quota-used-percent");
        if (usedPercent == null) {
            BigDecimal limit = firstDecimal(headers,
                    "x-ratelimit-limit-tokens", "x-ratelimit-limit-requests");
            BigDecimal remaining = firstDecimal(headers,
                    "x-ratelimit-remaining-tokens", "x-ratelimit-remaining-requests");
            if (limit != null && remaining != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                usedPercent = limit.subtract(remaining)
                        .multiply(new BigDecimal("100"))
                        .divide(limit, 2, RoundingMode.HALF_UP);
            }
        }

        Long resetAt = firstResetAt(headers,
                "x-codex-primary-reset-at",
                "x-openai-quota-reset-at",
                "x-quota-reset-at",
                "x-ratelimit-reset-tokens",
                "x-ratelimit-reset-requests");
        if (resetAt == null) {
            BigDecimal afterSeconds = firstDecimal(headers,
                    "x-codex-primary-reset-after-seconds",
                    "x-openai-quota-reset-after-seconds");
            if (afterSeconds != null) {
                resetAt = System.currentTimeMillis()
                        + afterSeconds.multiply(new BigDecimal("1000")).longValue();
            }
        }
        if (usedPercent == null && resetAt == null) return;

        ProviderAccountEntity entity = getRequired(accountId);
        if (usedPercent != null) {
            BigDecimal bounded = usedPercent.max(BigDecimal.ZERO)
                    .min(new BigDecimal("100"));
            entity.setQuotaUsedPercent(scale(bounded));
            if (bounded.compareTo(new BigDecimal("100")) >= 0) {
                entity.setQuotaStatus(ProviderQuotaStatus.EXHAUSTED);
                entity.setStatus(ProviderAccountStatus.EXHAUSTED);
            } else if (entity.getAlertThresholdPercent() != null
                    && bounded.compareTo(entity.getAlertThresholdPercent()) >= 0) {
                entity.setQuotaStatus(ProviderQuotaStatus.WARNING);
            } else {
                entity.setQuotaStatus(ProviderQuotaStatus.OK);
            }
            boolean alert = Boolean.TRUE.equals(entity.getAlertEnabled())
                    && bounded.compareTo(entity.getAlertThresholdPercent() == null
                            ? DEFAULT_ALERT_THRESHOLD : entity.getAlertThresholdPercent()) >= 0;
            entity.setAlertStatus(alert
                    ? (bounded.compareTo(new BigDecimal("100")) >= 0 ? "TRIGGERED" : "WARNING")
                    : "NONE");
        }
        if (resetAt != null) entity.setQuotaResetAt(resetAt);
        entity.setQuotaUpdatedAt(System.currentTimeMillis());
        accountMapper.updateById(entity);
    }

    @Transactional
    public ProviderUsageSummaryDTO usageSummary() {
        List<ProviderAccountDTO> accounts = listAccounts(null);
        int providerCount = (int) accounts.stream().map(ProviderAccountDTO::providerId).distinct().count();
        int enabled = (int) accounts.stream().filter(ProviderAccountDTO::enabled).count();
        int active = (int) accounts.stream()
                .filter(a -> ProviderAccountStatus.ACTIVE.equals(a.status())).count();
        int alerts = (int) accounts.stream()
                .filter(a -> !"NONE".equals(a.alertStatus())).count();
        return new ProviderUsageSummaryDTO(
                providerCount,
                accounts.size(),
                enabled,
                active,
                alerts,
                accounts.stream().mapToLong(ProviderAccountDTO::promptTokens).sum(),
                accounts.stream().mapToLong(ProviderAccountDTO::completionTokens).sum(),
                accounts.stream().mapToLong(ProviderAccountDTO::totalTokens).sum(),
                accounts);
    }

    private ProviderAccountEntity newBaseEntity(String providerId, String label,
                                                String authType, int priority) {
        ProviderAccountEntity entity = new ProviderAccountEntity();
        entity.setProviderId(providerId);
        entity.setLabel(label);
        entity.setAuthType(authType);
        entity.setEnabled(true);
        entity.setPriority(priority);
        entity.setStatus(ProviderAccountStatus.AVAILABLE);
        entity.setQuotaStatus(ProviderQuotaStatus.UNKNOWN);
        entity.setAlertEnabled(true);
        entity.setAlertThresholdPercent(DEFAULT_ALERT_THRESHOLD);
        entity.setAlertStatus("NONE");
        entity.setPromptTokens(0L);
        entity.setCompletionTokens(0L);
        entity.setTotalTokens(0L);
        return entity;
    }

    private String encrypt(CredentialPayload payload) {
        try {
            return settingCrypto.encrypt(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new MateClawException("Failed to encrypt provider account credentials");
        }
    }

    private CredentialPayload readCredentialPayload(ProviderAccountEntity entity) {
        try {
            String json = settingCrypto.decrypt(entity.getCredentialJson());
            return objectMapper.readValue(json, CredentialPayload.class);
        } catch (Exception e) {
            throw new MateClawException("Stored credentials for provider account " + entity.getId()
                    + " cannot be decrypted or parsed");
        }
    }

    private int nextPriority(String providerId) {
        List<ProviderAccountEntity> rows = accountMapper.selectList(
                new LambdaQueryWrapper<ProviderAccountEntity>()
                        .eq(ProviderAccountEntity::getProviderId, providerId)
                        .orderByDesc(ProviderAccountEntity::getPriority)
                        .last("LIMIT 1"));
        if (rows.isEmpty() || rows.get(0).getPriority() == null) return 1;
        return rows.get(0).getPriority() + 1;
    }

    private ProviderAccountEntity getRequired(Long accountId) {
        if (accountId == null) throw new MateClawException("accountId is required");
        ProviderAccountEntity entity = accountMapper.selectById(accountId);
        if (entity == null) throw notFound(accountId);
        return entity;
    }

    private MateClawException notFound(Long accountId) {
        return new MateClawException(404, "Provider account not found: " + accountId);
    }

    /**
     * A lazily imported account has two copies of its credential during the
     * compatibility window: the normalized account row and the old provider
     * columns. Removing only the account row makes the next list request import
     * it again. Clear the old copy only when it still belongs to this exact
     * account, so deleting one pooled account never disconnects another one.
     */
    private void clearOwnedLegacyCredential(ProviderAccountEntity account) {
        ModelProviderEntity legacy = legacyProviderMapper.selectById(account.getProviderId());
        if (legacy == null) return;

        if (AUTH_OAUTH.equalsIgnoreCase(account.getAuthType())
                && ownsLegacyOAuthCredential(account, legacy)) {
            int updated = legacyProviderMapper.update(null,
                    new LambdaUpdateWrapper<ModelProviderEntity>()
                            .eq(ModelProviderEntity::getProviderId, account.getProviderId())
                            .set(ModelProviderEntity::getOauthAccessToken, null)
                            .set(ModelProviderEntity::getOauthRefreshToken, null)
                            .set(ModelProviderEntity::getOauthExpiresAt, null)
                            .set(ModelProviderEntity::getOauthAccountId, null));
            if (updated == 0) {
                throw new MateClawException("Failed to disconnect legacy OAuth credential for provider "
                        + account.getProviderId());
            }
            legacy.setOauthAccessToken(null);
            legacy.setOauthRefreshToken(null);
            legacy.setOauthExpiresAt(null);
            legacy.setOauthAccountId(null);
            return;
        }

        if (AUTH_API_KEY.equalsIgnoreCase(account.getAuthType())
                && ownsLegacyApiKey(account, legacy)) {
            int updated = legacyProviderMapper.update(null,
                    new LambdaUpdateWrapper<ModelProviderEntity>()
                            .eq(ModelProviderEntity::getProviderId, account.getProviderId())
                            .set(ModelProviderEntity::getApiKey, null));
            if (updated == 0) {
                throw new MateClawException("Failed to disconnect legacy API key for provider "
                        + account.getProviderId());
            }
            legacy.setApiKey(null);
        }
    }

    private boolean ownsLegacyOAuthCredential(ProviderAccountEntity account,
                                              ModelProviderEntity legacy) {
        if (!StringUtils.hasText(legacy.getOauthAccessToken())) return false;
        if (StringUtils.hasText(account.getExternalAccountId())
                && StringUtils.hasText(legacy.getOauthAccountId())) {
            return account.getExternalAccountId().trim()
                    .equals(legacy.getOauthAccountId().trim());
        }
        if (!Boolean.TRUE.equals(account.getLegacyImport())) return false;
        CredentialPayload payload = readCredentialPayload(account);
        return Objects.equals(payload.accessToken(), legacy.getOauthAccessToken());
    }

    private boolean ownsLegacyApiKey(ProviderAccountEntity account,
                                     ModelProviderEntity legacy) {
        if (!Boolean.TRUE.equals(account.getLegacyImport())
                || !hasUsableLegacyApiKey(legacy.getApiKey())) {
            return false;
        }
        CredentialPayload payload = readCredentialPayload(account);
        return Objects.equals(payload.apiKey(), legacy.getApiKey().trim());
    }

    private void importAllLegacyProviders() {
        for (ModelProviderEntity provider : legacyProviderMapper.selectList(null)) {
            importLegacyProvider(provider);
        }
    }

    private void importLegacyProvider(String providerId) {
        ModelProviderEntity legacy = legacyProviderMapper.selectById(providerId);
        if (legacy != null) importLegacyProvider(legacy);
    }

    /** Idempotent, non-destructive import. Legacy columns remain untouched. */
    private void importLegacyProvider(ModelProviderEntity legacy) {
        Long count = accountMapper.selectCount(new LambdaQueryWrapper<ProviderAccountEntity>()
                .eq(ProviderAccountEntity::getProviderId, legacy.getProviderId())
                .eq(ProviderAccountEntity::getLegacyImport, true));
        if (count != null && count > 0) return;

        boolean oauth = AUTH_OAUTH.equalsIgnoreCase(legacy.getAuthType())
                && StringUtils.hasText(legacy.getOauthAccessToken());
        boolean apiKey = hasUsableLegacyApiKey(legacy.getApiKey());
        if (!oauth && !apiKey) return;

        ProviderAccountEntity entity = newBaseEntity(
                legacy.getProviderId(),
                (StringUtils.hasText(legacy.getName()) ? legacy.getName() : legacy.getProviderId()) + " (legacy)",
                oauth ? AUTH_OAUTH : AUTH_API_KEY,
                nextPriority(legacy.getProviderId()));
        entity.setExternalAccountId(trimToNull(legacy.getOauthAccountId()));
        entity.setLegacyImport(true);
        // Account availability is independent from whether the provider card
        // is shown in the catalog. A configured legacy credential must enter
        // the pool so its usage can be attributed immediately; provider/model
        // selection still decides whether it can receive a request at all.
        entity.setEnabled(true);
        entity.setStatus(ProviderAccountStatus.AVAILABLE);
        entity.setCredentialJson(encrypt(oauth
                ? new CredentialPayload(null, legacy.getOauthAccessToken(),
                        legacy.getOauthRefreshToken(), legacy.getOauthExpiresAt())
                : new CredentialPayload(legacy.getApiKey().trim(), null, null, null)));
        try {
            accountMapper.insert(entity);
            log.info("Imported legacy credentials into provider account pool for provider={}",
                    legacy.getProviderId());
        } catch (DuplicateKeyException ignored) {
            // Another concurrent request completed the same lazy import.
        }
    }

    private void releaseExpiredQuota(String providerId) {
        long now = System.currentTimeMillis();
        LambdaUpdateWrapper<ProviderAccountEntity> update = new LambdaUpdateWrapper<ProviderAccountEntity>()
                .eq(ProviderAccountEntity::getEnabled, true)
                .in(ProviderAccountEntity::getStatus,
                        ProviderAccountStatus.COOLDOWN, ProviderAccountStatus.EXHAUSTED)
                .isNotNull(ProviderAccountEntity::getQuotaResetAt)
                .le(ProviderAccountEntity::getQuotaResetAt, now)
                .set(ProviderAccountEntity::getStatus, ProviderAccountStatus.AVAILABLE)
                .set(ProviderAccountEntity::getQuotaStatus, ProviderQuotaStatus.UNKNOWN)
                .set(ProviderAccountEntity::getQuotaUsedPercent, null)
                .set(ProviderAccountEntity::getAlertStatus, "NONE")
                .set(ProviderAccountEntity::getLastErrorCode, null)
                .set(ProviderAccountEntity::getLastErrorMessage, null);
        if (StringUtils.hasText(providerId)) {
            update.eq(ProviderAccountEntity::getProviderId, providerId);
        }
        accountMapper.update(null, update);
    }

    private static boolean hasUsableLegacyApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) return false;
        String value = apiKey.trim();
        return !value.contains("*")
                && !"configure-in-admin-ui".equalsIgnoreCase(value)
                && !"your-dashscope-api-key-here".equalsIgnoreCase(value)
                && !"your-api-key-here".equalsIgnoreCase(value);
    }

    private static String normalizeQuotaStatus(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : ProviderQuotaStatus.UNKNOWN;
        if (!QUOTA_STATUSES.contains(normalized)) {
            throw new MateClawException("Unsupported quotaStatus: " + value);
        }
        return normalized;
    }

    private static boolean isQuotaBlocked(ProviderAccountEntity entity) {
        return ProviderQuotaStatus.RATE_LIMITED.equals(entity.getQuotaStatus())
                || ProviderQuotaStatus.EXHAUSTED.equals(entity.getQuotaStatus());
    }

    private static boolean isFuture(Long epochMs) {
        return epochMs != null && epochMs > System.currentTimeMillis();
    }

    private static void validatePercent(BigDecimal value, String field) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(new BigDecimal("100")) > 0)) {
            throw new MateClawException(field + " must be between 0 and 100");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) throw new MateClawException(field + " is required");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static BigDecimal firstDecimal(HttpHeaders headers, String... names) {
        for (String name : names) {
            String raw = headers.getFirst(name);
            if (!StringUtils.hasText(raw)) continue;
            try {
                return new BigDecimal(raw.trim().replace("%", ""));
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static Long firstResetAt(HttpHeaders headers, String... names) {
        for (String name : names) {
            String raw = headers.getFirst(name);
            if (!StringUtils.hasText(raw)) continue;
            String value = raw.trim();
            try {
                long epoch = Long.parseLong(value);
                if (epoch < 1_000_000_000L) {
                    return System.currentTimeMillis() + epoch * 1000L;
                }
                return epoch < 10_000_000_000L ? epoch * 1000L : epoch;
            } catch (NumberFormatException ignored) { }
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (Exception ignored) { }
            Matcher matcher = RESET_DURATION.matcher(value);
            if (matcher.matches() && matcher.group(0) != null && !matcher.group(0).isBlank()) {
                double seconds = decimal(matcher.group(1)) * 3600
                        + decimal(matcher.group(2)) * 60
                        + decimal(matcher.group(3));
                return System.currentTimeMillis() + (long) (seconds * 1000);
            }
        }
        return null;
    }

    private static double decimal(String value) {
        return value == null ? 0 : Double.parseDouble(value);
    }

    private record CredentialPayload(String apiKey, String accessToken,
                                     String refreshToken, Long expiresAt) {}
}
