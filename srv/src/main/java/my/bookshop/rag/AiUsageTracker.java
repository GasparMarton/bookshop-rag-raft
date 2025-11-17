package my.bookshop.rag;

import dev.langchain4j.model.output.TokenUsage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import my.bookshop.repository.AiUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Collects token usage statistics per AI model and keeps a bounded recent history.
 */
@Component
public class AiUsageTracker {

	private static final Logger logger = LoggerFactory.getLogger(AiUsageTracker.class);

	private static final int MAX_RECORDS = 200;

	private final ConcurrentLinkedDeque<AiUsageRecord> history = new ConcurrentLinkedDeque<>();
	private final AiUsageRepository repository;

	public AiUsageTracker(AiUsageRepository repository) {
		this.repository = repository;
	}

	public void recordUsage(String modelName, TokenUsage usage) {
		if (usage == null || modelName == null || modelName.isBlank()) {
			return;
		}
		int input = usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
		int output = usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
		int total = usage.totalTokenCount() == null ? input + output : usage.totalTokenCount();

		Instant timestamp = Instant.now();
		AiUsageRecord record = new AiUsageRecord(modelName, timestamp, input, output, total);
		history.addFirst(record);
		while (history.size() > MAX_RECORDS) {
			history.removeLast();
		}

		logger.debug("AI model '{}' consumed {} tokens (input={}, output={})", modelName, total, input, output);
		persist(record);
	}

	/**
	 * @return immutable snapshot of the most recent usage records (latest first).
	 */
	public List<AiUsageRecord> getRecentRecords() {
		return List.copyOf(history);
	}

	/**
	 * @return aggregated total token consumption per model across the current history window.
	 */
	public Map<String, Integer> getTotalTokensByModel() {
		return history.stream()
				.collect(Collectors.groupingBy(
						AiUsageRecord::getModelName,
						Collectors.summingInt(AiUsageRecord::getTotalTokens)));
	}

	private void persist(AiUsageRecord record) {
		if (repository == null || record == null) {
			return;
		}
		repository.saveRecord(
				record.getModelName(),
				record.getInputTokens(),
				record.getOutputTokens(),
				record.getTotalTokens());
	}
}
