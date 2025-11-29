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
		int batchSize = 500; // Process in batches to avoid hitting API limits

		for (int i = 0; i < chunks.size(); i += batchSize) {
			int end = Math.min(chunks.size(), i + batchSize);
			List<BookTextChunk> batchChunks = chunks.subList(i, end);
			List<String> batchTexts = batchChunks.stream().map(BookTextChunk::text).toList();

			try {
				List<double[]> batchVectors = aiClient.embed(batchTexts);

				if (batchVectors.size() != batchChunks.size()) {
					logger.warn("Mismatch in embedding count for book {} batch {}-{}. Expected {}, got {}",
							book.getTitle(), i, end, batchChunks.size(), batchVectors.size());
					continue;
				}

				for (int j = 0; j < batchChunks.size(); j++) {
					double[] vector = batchVectors.get(j);
					if (vector.length > 0) {
						payloads.add(new ChunkPersistRequest(batchChunks.get(j), vector));
					}
				}
			} catch (Exception e) {
				logger.error("Batch embedding failed for book {} batch {}-{}: {}",
						book.getTitle(), i, end, e.getMessage());
				// Continue to next batch? or fail?
				// Current logic: continue, so we save what we can.
			}
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
