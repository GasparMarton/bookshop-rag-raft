package my.bookshop.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import my.bookshop.repository.bookshop.BookContentChunkRepository;
import my.bookshop.repository.bookshop.BookshopBooksRepository;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

	private final RagAiClient aiClient;
	private final BookshopBooksRepository bookshopBooksRepository;
	private final BookContentChunkRepository chunkRepository;

	public RagRetrievalService(RagAiClient aiClient,
			BookshopBooksRepository bookshopBooksRepository,
			BookContentChunkRepository chunkRepository) {
		this.aiClient = aiClient;
		this.bookshopBooksRepository = bookshopBooksRepository;
		this.chunkRepository = chunkRepository;
	}

	public double[] embedForQuery(String text) {
		return aiClient.embed(text);
	}

	public List<TextSegment> similaritySearch(double[] vector) {
		if (chunkRepository == null || bookshopBooksRepository == null) {
			return List.of();
		}
		List<BookChunkMatch> matches = chunkRepository.findSimilarChunks(vector, 12);
		if (matches.isEmpty()) {
			return List.of();
		}
		Set<String> bookIds = matches.stream()
				.map(BookChunkMatch::bookId)
				.collect(Collectors.toSet());
		Map<String, cds.gen.my.bookshop.Books> summaries = bookshopBooksRepository.findSummariesByIds(bookIds);
		List<TextSegment> contexts = new ArrayList<>(matches.size());
		for (BookChunkMatch match : matches) {
			cds.gen.my.bookshop.Books book = summaries.get(match.bookId());
			contexts.add(toSegment(match, book));
		}
		return contexts;
	}

	private TextSegment toSegment(BookChunkMatch match, cds.gen.my.bookshop.Books book) {
		StringBuilder text = new StringBuilder();
		String title = book == null ? null : book.getTitle();
		if (title != null && !title.isBlank()) {
			text.append(title.trim()).append(" - ");
		}
		if (match.content() != null && !match.content().isBlank()) {
			text.append(match.content());
		} else if (book != null && book.getDescr() != null && !book.getDescr().isBlank()) {
			text.append(book.getDescr());
		} else {
			text.append("No description available.");
		}
		Metadata metadata = new Metadata();
		if (match.bookId() != null) {
			metadata.put("bookId", match.bookId());
		}
		if (title != null) {
			metadata.put("title", title);
		}
		metadata.put("similarity", match.similarity());
		metadata.put("chunkIndex", match.chunkIndex());
		metadata.put("chunkSource", match.source().name());
		if (book != null && book.getDescr() != null) {
			metadata.put("excerpt", book.getDescr());
		}
		if (match.content() != null) {
			metadata.put("chunk", match.content());
		}
		return TextSegment.from(text.toString(), metadata);
	}
}
