package my.bookshop.rag;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RagConfiguration {

    private final OpenAIProperties openAIProperties;
    private final GoogleColabProperties googleColabProperties;
    private final AiUsageTracker usageTracker;

    public RagConfiguration(OpenAIProperties openAIProperties, GoogleColabProperties googleColabProperties,
            AiUsageTracker usageTracker) {
        this.openAIProperties = openAIProperties;
        this.googleColabProperties = googleColabProperties;
        this.usageTracker = usageTracker;
    }

    @Bean("openAiClient")
    @Primary
    public RagAiClient openAiClient() {
        return new LangChainAiClient(
                openAIProperties.getBaseUrl(),
                openAIProperties.getApiKey(),
                openAIProperties.getChatModel(),
                openAIProperties.getEmbeddingModel(),
                usageTracker);
    }

    @Bean("raftClient")
    public RagAiClient raftClient() {
        return new LangChainAiClient(
                googleColabProperties.getBaseUrl(),
                googleColabProperties.getApiKey(),
                googleColabProperties.getChatModel(),
                null, // No embedding model for RAFT client
                usageTracker);
    }
}
