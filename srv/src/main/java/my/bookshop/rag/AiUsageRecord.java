package my.bookshop.rag;

import java.time.Instant;

/**
 * Represents a single AI model invocation and the tokens it consumed.
 */
public class AiUsageRecord {

	private final String modelName;
	private final Instant timestamp;
	private final int inputTokens;
	private final int outputTokens;
	private final int totalTokens;

	public AiUsageRecord(String modelName, Instant timestamp, int inputTokens, int outputTokens, int totalTokens) {
		this.modelName = modelName;
		this.timestamp = timestamp;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.totalTokens = totalTokens;
	}

	public String getModelName() {
		return modelName;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public int getInputTokens() {
		return inputTokens;
	}

	public int getOutputTokens() {
		return outputTokens;
	}

	public int getTotalTokens() {
		return totalTokens;
	}
}

