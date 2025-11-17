package my.bookshop.rag;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LangChainAiClient implements RagAiClient {

	private static final Logger logger = LoggerFactory.getLogger(LangChainAiClient.class);

	private final ChatLanguageModel chatModel;
	private final EmbeddingModel embeddingModel;
	private final AiUsageTracker usageTracker;
	private final String chatModelName;
	private final String embeddingModelName;

	public LangChainAiClient(OpenAIProperties properties, AiUsageTracker usageTracker) {
		this.usageTracker = usageTracker;
		this.chatModelName = properties.getChatModel();
		this.embeddingModelName = properties.getEmbeddingModel();
		String apiKey = properties.getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			// In local / test environments we may not have an API key.
			// In that case, fall back to a no-op client so that the
			// application and tests still start without external dependencies.
			logger.warn("OpenAI API key is not configured; RAG features will be disabled.");
			this.chatModel = null;
			this.embeddingModel = null;
			return;
		}

		this.chatModel = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(properties.getBaseUrl())
				.modelName(properties.getChatModel())
				.temperature(0.2)
				.responseFormat("json_object")
				.build();
		this.embeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.baseUrl(properties.getBaseUrl())
				.modelName(properties.getEmbeddingModel())
				.build();
	}

	@Override
	public double[] embed(String text) {
		if (text == null || text.isBlank() || embeddingModel == null) {
			return new double[0];
		}
		Response<Embedding> response = embeddingModel.embed(text);
		trackUsage(embeddingModelName, response);
		Embedding embedding = response == null ? null : response.content();
		if (embedding == null) {
			return new double[0];
		}
		float[] vector = embedding.vector();
		double[] result = new double[vector.length];
		for (int i = 0; i < vector.length; i++) {
			result[i] = vector[i];
		}
		return result;
	}

	@Override
	public String chat(List<ChatMessage> messages) {
		if (chatModel == null) {
			return "";
		}
		Response<AiMessage> response = chatModel.generate(messages);
		trackUsage(chatModelName, response);
		if (response == null || response.content() == null) {
			return "";
		}
		AiMessage message = response.content();
		return message == null ? "" : message.text();
	}

	private void trackUsage(String modelName, Response<?> response) {
		if (usageTracker == null || response == null) {
			return;
		}
		usageTracker.recordUsage(modelName, response.tokenUsage());
	}
}
