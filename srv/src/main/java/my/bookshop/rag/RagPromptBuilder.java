package my.bookshop.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;

@Component
public class RagPromptBuilder {

	private static final Prompt SYSTEM_PROMPT = Prompt.from("""
You are the SAP Bookshop research assistant for the browse-books page.
Goals:
1. Use the retrieved book context as factual evidence. Never invent details or cite books not provided.
2. Consider the ongoing chat history and the latest user question when forming the reply.
3. Quote book IDs in the response payload whenever a recommendation or citation references that book.

Response contract (always valid JSON):
{
  "reply": "<clear natural-language answer referencing the evidence>",
  "ids": ["<book-id-1>", "<book-id-2>"]
}
Rules:
- If you reference no books, return an empty ids array.
- If you lack enough context, say so in reply and keep ids empty.
- Do not include markdown, code fences, or extra keys.
""");

	public List<ChatMessage> buildMessages(List<Map<String, Object>> history, List<TextSegment> contexts,
			String message) {
		String contextBlock = buildContextBlock(contexts);

		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SYSTEM_PROMPT.toSystemMessage());
		messages.addAll(toMessages(history));
		messages.add(buildUserPrompt(contextBlock, message).toUserMessage());
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

	private String buildContextBlock(List<TextSegment> contexts) {
		if (contexts.isEmpty()) {
			return "No matching passages found.";
		}
		return contexts.stream()
				.map(segment -> {
					Metadata metadata = segment.metadata();
					String id = metadata != null ? metadata.getString("bookId") : null;
					String title = metadata != null ? metadata.getString("title") : null;
					Double similarity = metadata != null ? metadata.getDouble("similarity") : null;
					String excerpt = metadata != null && metadata.get("excerpt") != null
							? metadata.get("excerpt")
							: segment.text();
					return String.format("- Book: %s (ID %s, similarity %.3f)%n%s",
							title == null ? "Unknown" : title,
							id == null ? "n/a" : id,
							similarity == null ? 0.0 : similarity,
							excerpt == null ? "" : truncate(excerpt, 600));
				})
				.collect(Collectors.joining("\n\n"));
	}

	private Prompt buildUserPrompt(String contextBlock, String question) {
		return StructuredPromptProcessor.toPrompt(new RagUserPrompt(question, contextBlock));
	}

	private String truncate(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}
	@StructuredPrompt({
			"User question:",
			"{{question}}",
			"",
			"Retrieved book evidence:",
			"{{context}}",
			"",
			"Instructions:",
			"- Reference only the books listed in the evidence block.",
			"- If evidence is empty, explain that no relevant passages were found.",
			"- Keep the final reply short, confident, and grounded in the evidence."
	})
	private interface RagUserPromptTemplate {
		String question();

		String context();
	}

	private record RagUserPrompt(String question, String context) implements RagUserPromptTemplate {}
}
