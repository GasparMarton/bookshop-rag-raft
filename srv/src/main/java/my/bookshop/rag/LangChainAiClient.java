package my.bookshop.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LangChainAiClient {

	private final ChatLanguageModel chatModel;
	private final EmbeddingModel embeddingModel;

	public LangChainAiClient(OpenAIProperties properties) {
		this.chatModel = OpenAiChatModel.builder()
				.apiKey(properties.getApiKey())
				.baseUrl(properties.getBaseUrl())
				.modelName(properties.getChatModel())
				.temperature(0.2)
				.responseFormat("json_object")
				.build();
		this.embeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(properties.getApiKey())
				.baseUrl(properties.getBaseUrl())
				.modelName(properties.getEmbeddingModel())
				.build();
	}

	public double[] embed(String text) {
		if (text == null || text.isBlank()) {
			return new double[0];
		}
		Embedding embedding = embeddingModel.embed(text).content();
		return embedding == null ? new double[0] : embedding.vector();
	}

	public String chat(List<ChatMessage> messages) {
		AiMessage response = chatModel.generate(messages);
		return response == null ? "" : response.text();
	}
}

