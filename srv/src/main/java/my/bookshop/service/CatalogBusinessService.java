package my.bookshop.service;

import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.BooksAddReviewContext;
import cds.gen.catalogservice.Books_;
import cds.gen.catalogservice.ChatContext;
import cds.gen.catalogservice.ChatResult;
import cds.gen.catalogservice.ChatResultBook;
import cds.gen.catalogservice.SubmitOrderContext;
import cds.gen.catalogservice.Reviews;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.CdsVector;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnSelectListItem;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.request.FeatureTogglesInfo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import my.bookshop.MessageKeys;
import my.bookshop.RatingCalculator;
import my.bookshop.rag.BookEmbeddingService;
import my.bookshop.rag.LangChainAiClient;
import my.bookshop.repository.CatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CatalogBusinessService {

	private static final String SYSTEM_PROMPT = """
You are the Bookshop assistant. Ground every answer in the provided book excerpts.
Always respond with JSON:
{
  "reply": "natural language answer",
  "ids": ["optional list of book IDs to highlight"]
}
Return an empty array for ids when no books apply.
""";

	private static final TypeReference<List<Map<String, Object>>> HISTORY_TYPE = new TypeReference<>() {
	};

	private final CatalogRepository repository;
	private final Messages messages;
	private final FeatureTogglesInfo featureToggles;
	private final RatingCalculator ratingCalculator;
	private final BookEmbeddingService embeddingService;
	private final LangChainAiClient aiClient;
	private final ObjectMapper objectMapper;
	private final CqnAnalyzer analyzer;

	@Autowired
	public CatalogBusinessService(CatalogRepository repository, Messages messages,
			FeatureTogglesInfo featureToggles, RatingCalculator ratingCalculator, CqnAnalyzer analyzer,
			BookEmbeddingService embeddingService, LangChainAiClient aiClient, ObjectMapper objectMapper) {
		this.repository = repository;
		this.messages = messages;
		this.featureToggles = featureToggles;
		this.ratingCalculator = ratingCalculator;
		this.embeddingService = embeddingService;
		this.aiClient = aiClient;
		this.objectMapper = objectMapper;
		this.analyzer = analyzer;
	}

	public void ensureStockColumn(CdsReadEventContext context) {
		CqnSelect copy = CQL.copy(context.getCqn(), new Modifier() {
			@Override
			public List<CqnSelectListItem> items(List<CqnSelectListItem> items) {
				CqnSelectListItem stock = CQL.get("stock");
				if (!items.contains(stock)) {
					items.add(stock);
				}
				return items;
			}
		});
		context.setCqn(copy);
	}

	public void validateBeforeAddReview(Books_ ref, BooksAddReviewContext context) {
		String user = context.getUserInfo().getName();
		if (repository.hasReviewFromUser(ref, user)) {
			throw new ServiceException(ErrorStatuses.METHOD_NOT_ALLOWED, MessageKeys.REVIEW_ADD_FORBIDDEN);
		}
	}

	public Reviews addReview(Books_ ref, BooksAddReviewContext context) {
		String bookId = (String) analyzer().analyze(context.getCqn()).targetKeys().get(Books.ID);
		cds.gen.reviewservice.Reviews review = cds.gen.reviewservice.Reviews.create();
		review.setBookId(bookId);
		review.setRating(context.getRating());
		review.setTitle(context.getTitle());
		review.setText(context.getText());

		Reviews result = repository.insertReview(review);
		messages.success(MessageKeys.REVIEW_ADDED);
		return result;
	}

	public void updateRatingAfterReview(BooksAddReviewContext context) {
		ratingCalculator.setBookRating(context.getResult().getBookId());
	}

	public void applyDiscounts(Stream<Books> books) {
		boolean premium = featureToggles.isEnabled("discount");
		books.filter(b -> b.getTitle() != null).forEach(b -> discountBooksWithMoreThan111Stock(b, premium));
	}

	private void discountBooksWithMoreThan111Stock(Books book, boolean premium) {
		if (book.getStock() != null && book.getStock() > 111) {
			book.setTitle("%s -- %s%% discount".formatted(book.getTitle(), premium ? 14 : 11));
		}
	}

	public void determineReviewable(CdsReadEventContext context, List<Books> books) {
		String user = context.getUserInfo().getName();
		List<String> bookIds = books.stream().filter(b -> b.getId() != null).map(Books::getId)
				.collect(Collectors.toList());

		if (bookIds.isEmpty()) {
			return;
		}

		Set<String> reviewedBooks = repository.findReviewedBooksByUser(bookIds, user);
		for (Books book : books) {
			if (reviewedBooks.contains(book.getId())) {
				book.setIsReviewable(false);
			}
		}
	}

	public void handleBookCreatedOrUpdated(List<Books> books) {
		books.stream().map(Books::getId)
				.filter(id -> id != null && !id.isBlank())
				.forEach(embeddingService::reindexBook);
	}

	public void handleBookDeleted(List<Books> books) {
		books.stream().map(Books::getId)
				.filter(id -> id != null && !id.isBlank())
				.forEach(embeddingService::deleteEmbedding);
	}

	public SubmitOrderContext.ReturnType submitOrder(SubmitOrderContext context) {
		Integer quantity = context.getQuantity();
		String bookId = context.getBook();

		Books book = repository.fetchBook(bookId);
		int stock = book.getStock();

		if (stock >= quantity) {
			int updated = stock - quantity;
			repository.updateBookStock(bookId, updated);

			SubmitOrderContext.ReturnType result = SubmitOrderContext.ReturnType.create();
			result.setStock(updated);
			return result;
		}
		throw new ServiceException(ErrorStatuses.CONFLICT, MessageKeys.ORDER_EXCEEDS_STOCK, quantity);
	}

	public ChatResult handleChat(ChatContext context) {
		String message = context.getMessage();
		if (message == null || message.isBlank()) {
			ChatResult empty = ChatResult.create();
			empty.setReply("Please enter a question about the catalog.");
			return empty;
		}

		List<Map<String, Object>> historyTurns = parseHistory(context.getHistory());
		String queryText = buildQueryText(message, historyTurns);
		double[] vector = aiClient.embed(queryText.isBlank() ? message : queryText);
		List<BookContext> contexts = similaritySearch(vector);
		String contextBlock = buildContextBlock(contexts);

		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SystemMessage.from(SYSTEM_PROMPT));
		messages.addAll(toMessages(historyTurns));
		messages.add(UserMessage.from(buildUserPrompt(contextBlock, message)));

		String raw = aiClient.chat(messages);
		ChatPayload payload = parsePayload(raw);

		ChatResult result = ChatResult.create();
		result.setReply(payload.reply().isBlank() ? raw : payload.reply());
		result.setBooks(payload.books());
		return result;
	}

	private List<Map<String, Object>> parseHistory(String rawHistory) {
		if (rawHistory == null || rawHistory.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(rawHistory, HISTORY_TYPE);
		} catch (Exception e) {
			return List.of();
		}
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

	private String buildQueryText(String message, List<Map<String, Object>> history) {
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

	private String buildContextBlock(List<BookContext> contexts) {
		if (contexts.isEmpty()) {
			return "No matching passages found.";
		}
		return contexts.stream()
				.map(c -> String.format("- Book: %s (ID %s)\n%s",
						c.title() == null ? "Unknown" : c.title(),
						c.id(),
						c.descr() == null ? "" : c.descr()))
				.collect(Collectors.joining("\n\n"));
	}

	private String buildUserPrompt(String contextBlock, String question) {
		return """
Use the following context to answer.
Context:
%s

Question: %s

Respond ONLY with JSON matching:
{
  "reply": "<natural language response>",
  "ids": ["<optional book id>"]
}
If no books are relevant, use an empty array for ids.
""".formatted(contextBlock, question);
	}

	private ChatPayload parsePayload(String raw) {
		try {
			JsonNode node = objectMapper.readTree(raw);
			String reply = node.path("reply").asText();
			List<String> ids = new ArrayList<>();
			for (JsonNode idNode : node.withArray("ids")) {
				ids.add(idNode.asText());
			}
			List<ChatResultBook> resultBooks = repository.getByIds(ids).stream()
					.map(book -> {
						ChatResultBook resultBook = ChatResultBook.create();
						resultBook.putAll(book);
						return resultBook;
					})
					.toList();
			return new ChatPayload(reply, resultBooks);
		} catch (Exception e) {
			return new ChatPayload(raw, List.of());
		}
	}

	private List<BookContext> similaritySearch(double[] vector) {
		if (vector == null || vector.length == 0) {
			return List.of();
		}
		CdsVector cdsVector = toVector(vector);
		List<cds.gen.my.bookshop.Books> rows = repository.findSimilarBooks(cdsVector);
		List<BookContext> contexts = new ArrayList<>();
		for (cds.gen.my.bookshop.Books row : rows) {
			contexts.add(new BookContext(SYSTEM_PROMPT, SYSTEM_PROMPT, SYSTEM_PROMPT, 0));
		}
		return contexts;
	}

	private CdsVector toVector(double[] vector) {
		if (vector == null) {
			return null;
		}
		float[] values = new float[vector.length];
		for (int i = 0; i < vector.length; i++) {
			values[i] = (float) vector[i];
		}
		return CdsVector.of(values);
	}

	private record ChatPayload(String reply, List<ChatResultBook> books) {}

	private record BookContext(String id, String title, String descr, double similarity) {}

	private CqnAnalyzer analyzer() {
		if (analyzer == null) {
			throw new IllegalStateException("CqnAnalyzer is not configured");
		}
		return analyzer;
	}
}
