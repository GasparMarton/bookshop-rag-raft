package my.bookshop.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

	private static final Prompt SYSTEM_PROMPT = Prompt
			.from("""
					You are a helpful Bookshop Assistant.
					Answer the customer's question based strictly on the provided context.
					Keep your response short and concise. Do not offer to place holds, check live stock, or mention real-time availability.
					Determine if a database search is needed to find relevant books (e.g. if the user asks to find, show, or recommend books).
					Output your response as a JSON object with keys "reply" (string) and "vectorSearch" (boolean).
					If "vectorSearch" is true, explicitly state in the reply that you have searched for relevant books.
					""");

	public List<ChatMessage> buildMessages(List<Map<String, Object>> history, String message,
			List<TextSegment> context) {
		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SYSTEM_PROMPT.toSystemMessage());
		messages.addAll(toMessages(history));

		String userMessageText = message;
		if (context != null && !context.isEmpty()) {
			StringBuilder contextBuilder = new StringBuilder();
			contextBuilder.append("CONTEXT: ");
			for (TextSegment segment : context) {
				contextBuilder.append(segment.text()).append("\n\n");
			}
			contextBuilder.append("QUESTION: ").append(message);
			userMessageText = contextBuilder.toString();
		}

		messages.add(UserMessage.from(userMessageText));
		return messages;
	}

	public String buildQueryText(String message, List<Map<String, Object>> history) {
		StringBuilder builder = new StringBuilder(message.trim());
		int added = 0;
		for (int i = history.size() - 1; i >= 0 && added < 2; i--) {
			Map<String, Object> turn = history.get(i);
			if ("user".equals(turn.get("role"))) {
				Object content = turn.get("content");
				if (content != null) {
					builder.append(' ').append(content.toString());
					added++;
				}
			}
		}
		return builder.toString().trim();
	}

	private List<ChatMessage> toMessages(List<Map<String, Object>> history) {
		if (history.isEmpty()) {
			return List.of();
		}
		List<ChatMessage> messages = new ArrayList<>();
		int start = Math.max(0, history.size() - 6);
		for (int i = start; i < history.size(); i++) {
			Map<String, Object> turn = history.get(i);
			String role = String.valueOf(turn.get("role"));
			Object contentRaw = turn.get("content");
			String content = contentRaw == null ? null : contentRaw.toString();
			if (content == null || content.isBlank()) {
				continue;
			}
			if ("user".equals(role)) {
				messages.add(UserMessage.from(content));
			} else if ("assistant".equals(role)) {
				messages.add(AiMessage.from(content));
			}
		}
		return messages;
	}
}
