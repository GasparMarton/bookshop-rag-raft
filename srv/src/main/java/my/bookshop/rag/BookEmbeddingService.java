package my.bookshop.rag;

import cds.gen.my.bookshop.Books;
import java.util.ArrayList;
import java.util.List;
import my.bookshop.repository.bookshop.BookContentChunkRepository;
import my.bookshop.repository.bookshop.BookContentChunkRepository.ChunkPersistRequest;
import my.bookshop.repository.bookshop.BookshopBooksRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BookEmbeddingService {

	private static final Logger logger = LoggerFactory.getLogger(BookEmbeddingService.class);

	@Autowired
	private BookshopBooksRepository bookshopBooksRepository;

	@Autowired
	private BookContentChunkRepository chunkRepository;

	@Autowired
	private RagAiClient aiClient;

	private final BookTextChunker chunker = new BookTextChunker();

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
		chunkRepository.deleteChunksForBook(bookId);
	}

	public void deleteAllEmbeddings() {
		chunkRepository.deleteAll();
	}

	private void reindex(Books book) {
		List<BookTextChunk> chunks = chunker.chunk(book);
		if (chunks.isEmpty()) {
			logger.debug("No text found for book {}. Clearing embeddings.", book.getTitle());
			chunkRepository.deleteChunksForBook(book.getId());
			return;
		}
		List<ChunkPersistRequest> payloads = new ArrayList<>();
		for (BookTextChunk chunk : chunks) {
			double[] vector = aiClient.embed(chunk.text());
			if (vector.length == 0) {
				logger.warn("Embedding failed for book {} chunk {} ({})",
						book.getTitle(), chunk.index(), chunk.source());
				continue;
			}
			payloads.add(new ChunkPersistRequest(chunk, vector));
		}
		if (payloads.isEmpty()) {
			logger.warn("Embedding failed for book {}", book.getTitle());
			chunkRepository.deleteChunksForBook(book.getId());
			return;
		}
		chunkRepository.replaceChunks(book.getId(), payloads);
		logger.debug("Persisted {} chunks for book {}", payloads.size(), book.getTitle());
	}
}
