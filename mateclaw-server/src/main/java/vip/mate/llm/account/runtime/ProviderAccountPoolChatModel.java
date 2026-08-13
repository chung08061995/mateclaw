package vip.mate.llm.account.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.service.ProviderAccountService;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Account-level failover for one model provider.
 *
 * <p>The existing MateClaw fallback chain changes provider/model. This deep
 * module owns the smaller seam inside one provider: it leases enabled accounts
 * in priority order, records the account that actually consumed tokens, and
 * only lets an error escape to provider-level failover after every account is
 * unavailable. A streaming attempt is never switched after it emitted a
 * response chunk, avoiding duplicated partial answers.</p>
 */
@Slf4j
public final class ProviderAccountPoolChatModel implements ChatModel {

    private final String providerId;
    private final ProviderAccountService accountService;
    private final Function<ProviderAccountEntity, ChatModel> modelFactory;
    private final ChatOptions defaultOptions;

    public ProviderAccountPoolChatModel(String providerId,
                                        ProviderAccountService accountService,
                                        Function<ProviderAccountEntity, ChatModel> modelFactory,
                                        ChatOptions defaultOptions) {
        this.providerId = providerId;
        this.accountService = accountService;
        this.modelFactory = modelFactory;
        this.defaultOptions = defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<ProviderAccountEntity> accounts = accountService.listUsableAccounts(providerId);
        RuntimeException last = null;
        for (ProviderAccountEntity account : accounts) {
            try {
                ChatResponse response = modelFactory.apply(account).call(prompt);
                Usage usage = response != null && response.getMetadata() != null
                        ? response.getMetadata().getUsage() : null;
                recordSuccess(account, usage);
                return response;
            } catch (RuntimeException error) {
                last = error;
                recordFailure(account, error);
            }
        }
        if (last != null) throw last;
        throw poolUnavailable();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> attemptStream(accountService.listUsableAccounts(providerId), 0, prompt));
    }

    private Flux<ChatResponse> attemptStream(List<ProviderAccountEntity> accounts, int index, Prompt prompt) {
        if (index >= accounts.size()) {
            return Flux.error(poolUnavailable());
        }
        ProviderAccountEntity account = accounts.get(index);
        return Flux.defer(() -> {
            AtomicBoolean emitted = new AtomicBoolean(false);
            AtomicLong promptTokens = new AtomicLong();
            AtomicLong completionTokens = new AtomicLong();
            AtomicLong totalTokens = new AtomicLong();
            return modelFactory.apply(account).stream(prompt)
                    .doOnNext(response -> {
                        emitted.set(true);
                        Usage usage = response != null && response.getMetadata() != null
                                ? response.getMetadata().getUsage() : null;
                        if (usage != null) {
                            promptTokens.set(number(usage.getPromptTokens()));
                            completionTokens.set(number(usage.getCompletionTokens()));
                            totalTokens.set(number(usage.getTotalTokens()));
                        }
                    })
                    .doOnComplete(() -> {
                        accountService.markActive(account.getId());
                        accountService.recordSuccess(account.getId(), promptTokens.get(),
                                completionTokens.get(), totalTokens.get());
                    })
                    .onErrorResume(error -> {
                        recordFailure(account, error);
                        if (emitted.get()) return Flux.error(error);
                        log.warn("[ProviderAccountPool] provider={} account={} unavailable; trying next account",
                                providerId, account.getId());
                        return attemptStream(accounts, index + 1, prompt);
                    });
        });
    }

    private IllegalStateException poolUnavailable() {
        return new IllegalStateException(
                "provider_account_pool_unavailable: No usable account for provider " + providerId);
    }

    private void recordSuccess(ProviderAccountEntity account, Usage usage) {
        accountService.markActive(account.getId());
        accountService.recordSuccess(account.getId(),
                usage == null ? 0 : number(usage.getPromptTokens()),
                usage == null ? 0 : number(usage.getCompletionTokens()),
                usage == null ? 0 : number(usage.getTotalTokens()));
    }

    private void recordFailure(ProviderAccountEntity account, Throwable error) {
        String full = fullMessage(error);
        String lower = full.toLowerCase();
        String code;
        if (lower.contains("insufficient_quota") || lower.contains("quota exceeded")
                || lower.contains("credit balance") || lower.contains("billing")) {
            code = "QUOTA_EXHAUSTED";
        } else if (lower.contains("429") || lower.contains("rate_limit")
                || lower.contains("too many requests")) {
            code = "RATE_LIMITED";
        } else if (lower.contains("401") || lower.contains("unauthorized")
                || lower.contains("token_revoked") || lower.contains("invalid api key")) {
            code = "AUTH_ERROR";
        } else {
            code = "ERROR";
        }
        accountService.recordFailure(account.getId(), code, abbreviate(full), resetAt(error));
    }

    private static long number(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private static String fullMessage(Throwable error) {
        StringBuilder out = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) out.append(current.getMessage()).append(" | ");
            if (current instanceof WebClientResponseException web) {
                String body = web.getResponseBodyAsString();
                if (body != null) out.append(body).append(" | ");
            }
        }
        return out.toString();
    }

    private static String abbreviate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static Long resetAt(Throwable error) {
        HttpHeaders headers = null;
        if (error instanceof WebClientResponseException web) headers = web.getHeaders();
        if (error instanceof RestClientResponseException rest) headers = rest.getResponseHeaders();
        if (headers == null) return null;
        String retryAfter = headers.getFirst("retry-after");
        if (retryAfter != null) {
            try {
                return System.currentTimeMillis() + Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                try {
                    return ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli();
                } catch (Exception ignoredAgain) { }
            }
        }
        for (String name : List.of("x-ratelimit-reset-tokens", "x-ratelimit-reset-requests")) {
            String raw = headers.getFirst(name);
            if (raw == null) continue;
            try {
                return Instant.parse(raw).toEpochMilli();
            } catch (Exception ignored) { }
        }
        return null;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }
}
