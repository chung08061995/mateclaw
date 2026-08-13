package vip.mate.llm.account;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.runtime.ProviderAccountPoolChatModel;
import vip.mate.llm.account.service.ProviderAccountService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderAccountPoolChatModelTest {

    @Test
    void rotatesToSecondAccountAfterFirstIsRateLimited() {
        ProviderAccountService service = mock(ProviderAccountService.class);
        ProviderAccountEntity first = account(1L);
        ProviderAccountEntity second = account(2L);
        when(service.listUsableAccounts("openai-chatgpt")).thenReturn(List.of(first, second));

        Prompt prompt = mock(Prompt.class);
        ChatModel firstModel = mock(ChatModel.class);
        ChatModel secondModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(new DefaultUsage(11, 7, 18));
        when(firstModel.call(prompt)).thenThrow(new RuntimeException("429 rate_limit"));
        when(secondModel.call(prompt)).thenReturn(response);

        ProviderAccountPoolChatModel pool = pool(service,
                Map.of(1L, firstModel, 2L, secondModel));

        assertSame(response, pool.call(prompt));
        verify(service).recordFailure(eq(1L), eq("RATE_LIMITED"), contains("429"), nullable(Long.class));
        verify(service).markActive(2L);
        verify(service).recordSuccess(2L, 11, 7, 18);
    }

    @Test
    void rotatesStreamingRequestOnlyBeforeAnyChunkWasEmitted() {
        ProviderAccountService service = mock(ProviderAccountService.class);
        ProviderAccountEntity first = account(1L);
        ProviderAccountEntity second = account(2L);
        when(service.listUsableAccounts("openai-chatgpt")).thenReturn(List.of(first, second));

        Prompt prompt = mock(Prompt.class);
        ChatModel firstModel = mock(ChatModel.class);
        ChatModel secondModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(new DefaultUsage(5, 3, 8));
        when(firstModel.stream(prompt)).thenReturn(Flux.error(new RuntimeException("quota exceeded")));
        when(secondModel.stream(prompt)).thenReturn(Flux.just(response));

        ProviderAccountPoolChatModel pool = pool(service,
                Map.of(1L, firstModel, 2L, secondModel));

        assertSame(response, pool.stream(prompt).blockLast());
        verify(service).recordFailure(eq(1L), eq("QUOTA_EXHAUSTED"), contains("quota"), nullable(Long.class));
        verify(service).markActive(2L);
        verify(service).recordSuccess(2L, 5, 3, 8);
    }

    private static ProviderAccountPoolChatModel pool(ProviderAccountService service,
                                                      Map<Long, ChatModel> models) {
        return new ProviderAccountPoolChatModel("openai-chatgpt", service,
                account -> models.get(account.getId()), mock(ChatOptions.class));
    }

    private static ProviderAccountEntity account(long id) {
        ProviderAccountEntity account = new ProviderAccountEntity();
        account.setId(id);
        account.setProviderId("openai-chatgpt");
        return account;
    }
}
