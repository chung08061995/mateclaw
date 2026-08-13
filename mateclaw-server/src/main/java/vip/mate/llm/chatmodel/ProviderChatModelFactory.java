package vip.mate.llm.chatmodel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import vip.mate.exception.MateClawException;
import vip.mate.llm.account.model.ProviderAccountCredentials;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.runtime.ProviderAccountPoolChatModel;
import vip.mate.llm.account.service.ProviderAccountService;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelProtocol;
import vip.mate.llm.model.ModelProviderEntity;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.llm.oauth.OpenAIOAuthService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes {@link ChatModel} construction to the appropriate
 * {@link ChatModelBuilder} based on the provider's declared protocol.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Look up the {@link ModelProviderEntity} for {@code model.getProvider()}</li>
 *   <li>Map its {@code chatModel} string to a {@link ModelProtocol}</li>
 *   <li>Dispatch to the matching {@link ChatModelBuilder}; throw if none registered</li>
 * </ol>
 *
 * <p>This factory is the single seam that {@code AgentGraphBuilder} and
 * {@code ProviderInitProbe} share for building Spring AI clients. Keeping it
 * in the {@code llm} package preserves the dependency direction
 * {@code agent → llm} and lets the failover layer probe providers without
 * pulling in the {@code agent} package.</p>
 */
@Slf4j
@Component
public class ProviderChatModelFactory {

    private final Map<ModelProtocol, ChatModelBuilder> builders;
    private final ModelProviderService modelProviderService;
    private final ProviderAccountService providerAccountService;
    private final ObjectProvider<OpenAIOAuthService> openAIOAuthServiceProvider;

    @Autowired
    public ProviderChatModelFactory(List<ChatModelBuilder> allBuilders,
                                    ModelProviderService modelProviderService,
                                    ProviderAccountService providerAccountService,
                                    ObjectProvider<OpenAIOAuthService> openAIOAuthServiceProvider) {
        this.modelProviderService = modelProviderService;
        this.providerAccountService = providerAccountService;
        this.openAIOAuthServiceProvider = openAIOAuthServiceProvider;
        Map<ModelProtocol, ChatModelBuilder> map = new EnumMap<>(ModelProtocol.class);
        for (ChatModelBuilder b : allBuilders) {
            ChatModelBuilder previous = map.put(b.supportedProtocol(), b);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two ChatModelBuilders registered for the same protocol "
                                + b.supportedProtocol() + ": "
                                + previous.getClass().getName() + " vs " + b.getClass().getName());
            }
        }
        this.builders = Map.copyOf(map);
        log.info("[ProviderChatModelFactory] registered builders for protocols: {}", builders.keySet());
    }

    /** Compatibility constructor for lightweight tests and embedders. */
    public ProviderChatModelFactory(List<ChatModelBuilder> allBuilders,
                                    ModelProviderService modelProviderService,
                                    ProviderAccountService providerAccountService) {
        this(allBuilders, modelProviderService, providerAccountService, null);
    }

    /**
     * Build a {@link ChatModel} for the given runtime model, looking up the
     * provider on the fly. Throws {@link MateClawException} when no builder
     * is registered for the resolved protocol — callers should treat this as
     * a configuration error, not a transient failure.
     */
    public ChatModel buildFor(ModelConfigEntity model, RetryTemplate retry) {
        ModelProviderEntity provider = modelProviderService.getProviderConfig(model.getProvider());
        ModelProtocol protocol = ModelProtocol.fromChatModel(provider.getChatModel());
        ChatModelBuilder builder = builders.get(protocol);
        if (builder == null) {
            throw new MateClawException("err.agent.protocol_limited",
                    "No ChatModelBuilder registered for protocol: " + protocol.getId());
        }
        List<ProviderAccountEntity> accounts = providerAccountService
                .listUsableAccounts(provider.getProviderId());
        if (accounts.isEmpty()
                && !providerAccountService.hasConfiguredAccounts(provider.getProviderId())) {
            return builder.build(model, provider, retry);
        }
        org.springframework.ai.chat.prompt.ChatOptions options =
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                        .model(model.getModelName())
                        .temperature(model.getTemperature())
                        .build();
        return new ProviderAccountPoolChatModel(
                provider.getProviderId(),
                providerAccountService,
                account -> buildForAccount(builder, model, provider, account, retry),
                options);
    }

    private ChatModel buildForAccount(ChatModelBuilder builder,
                                      ModelConfigEntity model,
                                      ModelProviderEntity provider,
                                      ProviderAccountEntity account,
                                      RetryTemplate retry) {
        ModelProviderEntity scoped = providerForAccount(provider, account);
        if (builder instanceof ChatGPTResponsesChatModelBuilder chatGPTBuilder) {
            return chatGPTBuilder.build(model, scoped, retry,
                    headers -> providerAccountService.observeQuotaHeaders(account.getId(), headers));
        }
        return builder.build(model, scoped, retry);
    }

    /** Build a request-local provider snapshot; never mutate the catalog row. */
    private ModelProviderEntity providerForAccount(ModelProviderEntity provider,
                                                   ProviderAccountEntity account) {
        ProviderAccountCredentials credentials = providerAccountService
                .getDecryptedCredentials(account.getId());
        if (ModelProtocol.OPENAI_CHATGPT == ModelProtocol.fromChatModel(provider.getChatModel())
                && "oauth".equalsIgnoreCase(account.getAuthType())
                && openAIOAuthServiceProvider != null) {
            OpenAIOAuthService oauth = openAIOAuthServiceProvider.getIfAvailable();
            if (oauth != null) {
                credentials = oauth.ensureValidAccountCredentials(account.getId());
            }
        }
        ModelProviderEntity scoped = new ModelProviderEntity();
        BeanUtils.copyProperties(provider, scoped);
        if (credentials.apiKey() != null && !credentials.apiKey().isBlank()) {
            scoped.setApiKey(credentials.apiKey());
        }
        if (credentials.accessToken() != null && !credentials.accessToken().isBlank()) {
            scoped.setOauthAccessToken(credentials.accessToken());
            scoped.setOauthRefreshToken(credentials.refreshToken());
            scoped.setOauthExpiresAt(credentials.expiresAt());
            scoped.setOauthAccountId(credentials.providerAccountId());
        }
        return scoped;
    }
}
