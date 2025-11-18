package my.bookshop.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.BooksAddReviewContext;
import cds.gen.catalogservice.Books_;
import cds.gen.catalogservice.ChatContext;
import cds.gen.catalogservice.ChatResult;
import cds.gen.catalogservice.ChatResultBook;
import cds.gen.catalogservice.Reviews;
import cds.gen.catalogservice.SubmitOrderContext;
import dev.langchain4j.data.segment.TextSegment;
import my.bookshop.MessageKeys;
import my.bookshop.RatingCalculator;
import my.bookshop.rag.BookEmbeddingService;
import my.bookshop.rag.RagAiClient;
import my.bookshop.rag.RagPromptBuilder;
import my.bookshop.rag.RagRetrievalService;
import my.bookshop.repository.CatalogRepository;
import my.bookshop.repository.bookshop.BookContentChunkRepository;
import my.bookshop.repository.bookshop.BookshopBooksRepository;

@Service
public class CatalogBusinessService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogBusinessService.class);

	private static final TypeReference<List<Map<String, Object>>> HISTORY_TYPE = new TypeReference<>() {
	};

	private final CatalogRepository repository;
	private final Messages messages;
	private final FeatureTogglesInfo featureToggles;
	private final RatingCalculator ratingCalculator;
	private final BookEmbeddingService embeddingService;
	private final RagAiClient aiClient;
	private final ObjectMapper objectMapper;
	private final CqnAnalyzer analyzer;
	private final RagRetrievalService ragRetrievalService;
	private final RagPromptBuilder ragPromptBuilder;

	@Autowired
	public CatalogBusinessService(CatalogRepository repository, Messages messages,
			FeatureTogglesInfo featureToggles, RatingCalculator ratingCalculator, CqnAnalyzer analyzer,
			BookEmbeddingService embeddingService, RagAiClient aiClient, ObjectMapper objectMapper,
			RagRetrievalService ragRetrievalService, RagPromptBuilder ragPromptBuilder) {
		this.repository = repository;
		this.messages = messages;
		this.featureToggles = featureToggles;
		this.ratingCalculator = ratingCalculator;
		this.embeddingService = embeddingService;
		this.aiClient = aiClient;
		this.objectMapper = objectMapper;
		this.analyzer = analyzer;
		this.ragRetrievalService = ragRetrievalService;
		this.ragPromptBuilder = ragPromptBuilder;
	}

	public CatalogBusinessService(CatalogRepository repository, Messages messages,
			FeatureTogglesInfo featureToggles, RatingCalculator ratingCalculator, CqnAnalyzer analyzer,
			BookEmbeddingService embeddingService, RagAiClient aiClient, ObjectMapper objectMapper,
			BookshopBooksRepository bookshopBooksRepository, BookContentChunkRepository chunkRepository) {
		this(repository, messages, featureToggles, ratingCalculator, analyzer, embeddingService, aiClient, objectMapper,
				new RagRetrievalService(aiClient, bookshopBooksRepository, chunkRepository), new RagPromptBuilder());
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
		List<String> bookIds = collectBookIds(books);

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
		validBookIds(books).forEach(embeddingService::reindexBook);
	}

	public void handleBookDeleted(List<Books> books) {
		validBookIds(books).forEach(embeddingService::deleteEmbedding);
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
			logger.debug("Chat request rejected because message was empty.");
			return chatResult("Please enter a question about the catalog.", List.of());
		}

		List<Map<String, Object>> historyTurns = parseHistory(context.getHistory());
		String queryText = ragPromptBuilder.buildQueryText(message, historyTurns);
		double[] vector = ragRetrievalService.embedForQuery(queryText.isBlank() ? message : queryText);
		List<TextSegment> contexts = ragRetrievalService.similaritySearch(vector);
		var messages = ragPromptBuilder.buildMessages(historyTurns, contexts, message);

		String raw = aiClient.chat(messages);
		if (raw == null || raw.isBlank()) {
			logger.warn("RAG chat returned empty response; sending fallback to client.");
			return chatResult("RAG is currently unavailable. Please try again later or refine your question.", List.of());
		}
		ChatPayload payload = parsePayload(raw);

		return chatResult(payload.reply().isBlank() ? raw : payload.reply(), payload.books());
	}

	private List<Map<String, Object>> parseHistory(String rawHistory) {
		if (rawHistory == null || rawHistory.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(rawHistory, HISTORY_TYPE);
		} catch (Exception e) {
			logger.debug("Failed to parse chat history payload ({} chars); ignoring history.",
					rawHistory.length(), e);
			return List.of();
		}
	}

	private ChatPayload parsePayload(String raw) {
		try {
			JsonNode node = objectMapper.readTree(raw);
			String reply = node.path("reply").asText();
			List<String> ids = new ArrayList<>();
			for (JsonNode idNode : node.withArray("ids")) {
				ids.add(idNode.asText());
			}
			List<ChatResultBook> resultBooks = ids.isEmpty()
					? List.of()
					: repository.getChatResultBooks(ids);
			return new ChatPayload(reply, resultBooks);
		} catch (Exception e) {
			logger.warn("Failed to parse chat payload returned by RAG; sending raw text instead.", e);
			return new ChatPayload(raw, List.of());
		}
	}

	private record ChatPayload(String reply, List<ChatResultBook> books) {}

	private ChatResult chatResult(String reply, List<ChatResultBook> resultBooks) {
		ChatResult result = ChatResult.create();
		result.setReply(reply);
		result.setBooks(resultBooks == null ? List.of() : resultBooks);
		return result;
	}

	private List<String> collectBookIds(List<Books> books) {
		return validBookIds(books).distinct().toList();
	}

	private Stream<String> validBookIds(List<Books> books) {
		if (books == null) {
			return Stream.empty();
		}
		return books.stream()
				.map(Books::getId)
				.filter(id -> id != null && !id.isBlank())
				.map(String::trim);
	}

	private CqnAnalyzer analyzer() {
		if (analyzer == null) {
			throw new IllegalStateException("CqnAnalyzer is not configured");
		}
		return analyzer;
	}
}
