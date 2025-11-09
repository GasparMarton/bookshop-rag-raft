package my.bookshop.rag;

import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.Books_;
import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BookEmbeddingService {

	private static final Logger logger = LoggerFactory.getLogger(BookEmbeddingService.class);

	private final PersistenceService db;
	private final LangChainAiClient aiClient;

	public BookEmbeddingService(PersistenceService db, LangChainAiClient aiClient) {
		this.db = db;
		this.aiClient = aiClient;
	}

	public void rebuildAll() {
		Result books = db.run(Select.from(Books_.CDS_NAME)
				.columns(b -> b.ID(), b -> b.title(), b -> b.descr(), b -> b.fullText()));
		books.streamOf(Books.class)
				.forEach(book -> reindex(book.getId(), book));
	}

	public void reindexBook(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		Result res = db.run(Select.from(Books_.CDS_NAME)
				.columns(b -> b.ID(), b -> b.title(), b -> b.descr(), b -> b.fullText())
				.byId(bookId));
		Books book = res.first(Books.class).orElse(null);
		reindex(bookId, book);
	}

	public void deleteEmbedding(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		db.run(Update.entity(Books_.CDS_NAME).byId(bookId)
				.data(Books.EMBEDDING, null));
	}

	private void reindex(String bookId, Books book) {
		if (book == null) {
			deleteEmbedding(bookId);
			return;
		}
		String text = buildText(book);
		if (text.isBlank()) {
			logger.debug("No text found for book {}. Clearing embedding.", book.getTitle());
			deleteEmbedding(book.getId());
			return;
		}
		double[] vector = aiClient.embed(text);
		if (vector.length == 0) {
			logger.warn("Embedding failed for book {}", book.getTitle());
			return;
		}
		db.run(Update.entity(Books_.CDS_NAME).byId(book.getId())
				.data(Books.EMBEDDING, toBigDecimals(vector)));
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

	private List<BigDecimal> toBigDecimals(double[] vector) {
		List<BigDecimal> values = new ArrayList<>(vector.length);
		for (double v : vector) {
			values.add(BigDecimal.valueOf(v));
		}
		return values;
	}
}
