package vip.mate.llm.chatgpt;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatGPTSynchronousUsageTest {

    @Test
    void attachesResponsesApiUsageToSynchronousChatResponse() {
        ChatGPTResponsesClient client = mock(ChatGPTResponsesClient.class);
        when(client.callResult(anyString(), anyList(), anyDouble(), anyList(),
                isNull(), isNull(), isNull()))
                .thenReturn(new ChatGPTResponsesClient.CallResult("done", 12, 8, 20));
        ChatGPTChatModel model = new ChatGPTChatModel(client, "gpt-test", 0.2);

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("hello"))));

        assertEquals("done", response.getResult().getOutput().getText());
        assertEquals(12, response.getMetadata().getUsage().getPromptTokens());
        assertEquals(8, response.getMetadata().getUsage().getCompletionTokens());
        assertEquals(20, response.getMetadata().getUsage().getTotalTokens());
    }
}
