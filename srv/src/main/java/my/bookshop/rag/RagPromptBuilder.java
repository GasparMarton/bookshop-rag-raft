package my.bookshop.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

	private static final Prompt SYSTEM_PROMPT = Prompt
			.from("""
					You are the SAP Bookshop research assistant for the browse-books page.
					Goals:
					1. Answer the user's question using the provided context if available.
					2. Determine if the user is asking for books or recommendations.
					3. If the user is asking for books, set "vectorSearch" to true. Otherwise, set it to false.
					Response contract (always valid JSON):
					{
					  "reply": "<clear natural-language answer>",
					  "vectorSearch": <true/false>
					}
					Rules:
					- If vectorSearch is true, the system will search for books and display them in a table. Your reply should introduce these results as if they are already presented to the user (e.g., "Here are some books about space"). Do not say "I will search" or ask for confirmation.
					- Do not include markdown, code fences, or extra keys.
					""");

	public List<ChatMessage> buildMessages(List<Map<String, Object>> history, String message,
			List<TextSegment> context) {
		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SYSTEM_PROMPT.toSystemMessage());
		messages.addAll(toMessages(history));

		StringBuilder contextText = new StringBuilder();
		if (context != null && !context.isEmpty()) {
			contextText.append("Context:\n");
			for (TextSegment segment : context) {
				contextText.append(segment.text()).append("\n\n");
			}
			contextText.append("\nQuestion: ");
		}

		messages.add(buildUserPrompt(contextText.toString() + message).toUserMessage());
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

	private Prompt buildUserPrompt(String question) {
		return StructuredPromptProcessor.toPrompt(new RagUserPrompt(question));
	}

	@StructuredPrompt({
			"User question:",
			"{{question}}"
	})
	private record RagUserPrompt(String question) {
	}
}
