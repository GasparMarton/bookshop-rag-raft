package my.bookshop.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cds.gen.my.bookshop.Books;
import my.bookshop.repository.bookshop.BookshopBooksRepository;

@Component
public class BookEmbeddingService {

	private static final Logger logger = LoggerFactory.getLogger(BookEmbeddingService.class);

	@Autowired
	private BookshopBooksRepository bookshopBooksRepository;

	@Autowired
	private RagAiClient aiClient;

	public void rebuildAll() {
		bookshopBooksRepository.findAllWithTextFields()
				.forEach(this::reindex);
	}

	public void reindexBook(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		bookshopBooksRepository.findByIdWithTextFields(bookId)
				.ifPresent(this::reindex);
	}

	public void deleteEmbedding(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		bookshopBooksRepository.clearEmbedding(bookId);
	}

	public void deleteAllEmbeddings() {
		bookshopBooksRepository.clearAllEmbeddings();
	}

	private void reindex(Books book) {
		String text = buildText(book);
		if (text.isBlank()) {
			logger.debug("No text found for book {}. Clearing embedding.", book.getTitle());
			bookshopBooksRepository.clearEmbedding(book.getId());
			return;
		}
		double[] vector = aiClient.embed(text);
		if (vector.length == 0) {
			logger.warn("Embedding failed for book {}", book.getTitle());
			return;
		}
		bookshopBooksRepository.updateEmbedding(book.getId(), vector);
	}

	private String buildText(Books book) {
		StringBuilder builder = new StringBuilder();
		if (book.getTitle() != null) {
			builder.append(book.getTitle()).append('.');
		}
		if (book.getDescr() != null) {
			builder.append(' ').append(book.getDescr());
		}
		if (book.getFullText() != null) {
			builder.append(' ').append(book.getFullText());
		}
		return builder.toString().replaceAll("\\s+", " ").trim();
	}
}
