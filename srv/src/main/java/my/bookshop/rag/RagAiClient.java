package my.bookshop.rag;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Abstraction for RAG-related AI operations (embeddings + chat).
 * <p>
 * Currently backed by LangChain4j / OpenAI via {@link LangChainAiClient}.
 */
public interface RagAiClient {

	double[] embed(String text);

	List<double[]> embed(List<String> texts);

	String chat(List<ChatMessage> messages);
}
