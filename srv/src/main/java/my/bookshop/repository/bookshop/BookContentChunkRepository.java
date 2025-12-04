package my.bookshop.repository.bookshop;

import static cds.gen.my.bookshop.Bookshop_.BOOK_CHUNKS;

import cds.gen.my.bookshop.BookChunks;
import com.sap.cds.CdsVector;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import my.bookshop.rag.BookChunkMatch;
import my.bookshop.rag.BookChunkSource;
import my.bookshop.rag.BookTextChunk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Persists and queries chunk-level embeddings for book content.
 */
@Repository
public class BookContentChunkRepository {

	@Autowired
	private PersistenceService db;

	public void replaceChunks(String bookId, List<ChunkPersistRequest> chunks) {
		deleteChunksForBook(bookId);
		if (chunks == null || chunks.isEmpty()) {
			return;
		}
		List<Map<String, Object>> entries = new ArrayList<>(chunks.size());
		for (ChunkPersistRequest request : chunks) {
			if (request == null || request.chunk() == null || request.embedding() == null
					|| request.embedding().length == 0) {
				continue;
			}
			Map<String, Object> row = new HashMap<>();
			row.put(BookChunks.BOOK_ID, bookId);
			row.put(BookChunks.CHUNK_INDEX, request.chunk().index());
			row.put(BookChunks.SOURCE, request.chunk().source().name());
			row.put(BookChunks.TEXT, request.chunk().text());
			row.put(BookChunks.EMBEDDING, toBigDecimals(request.embedding()));
			entries.add(row);
		}
		if (!entries.isEmpty()) {
			db.run(Insert.into(BOOK_CHUNKS).entries(entries));
		}
	}

	public void deleteChunksForBook(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		db.run(Delete.from(BOOK_CHUNKS)
				.where(chunk -> chunk.book_ID().eq(bookId)));
	}

	public void deleteAll() {
		db.run(Delete.from(BOOK_CHUNKS));
	}

	public List<BookChunkMatch> findSimilarChunks(double[] vector, int limit, double minSimilarity) {
		CdsVector cdsVector = toVector(vector);
		if (cdsVector == null) {
			return List.of();
		}
		var similarity = CQL.cosineSimilarity(CQL.get(BookChunks.EMBEDDING), CQL.val(cdsVector));
		CqnSelect select = Select.from(BOOK_CHUNKS)
				.columns(chunk -> chunk.ID(),
						chunk -> chunk.book_ID(),
						chunk -> chunk.chunkIndex(),
						chunk -> chunk.source(),
						chunk -> chunk.text(),
						chunk -> similarity.as("similarity"))
				.where(chunk -> chunk.embedding().isNotNull().and(similarity.ge(minSimilarity)))
				.orderBy(chunk -> chunk.get("similarity").desc())
				.limit(limit);
		Result result = db.run(select);
		List<BookChunkMatch> matches = new ArrayList<>();
		for (Row row : result) {
			matches.add(new BookChunkMatch(
					asString(row, BookChunks.ID),
					asString(row, BookChunks.BOOK_ID),
					defaultInt((Number) row.get(BookChunks.CHUNK_INDEX)),
					BookChunkSource.from(asString(row, BookChunks.SOURCE)),
					asString(row, BookChunks.TEXT),
					toDouble((Number) row.get("similarity"))));
		}
		return matches;
	}

	private List<BigDecimal> toBigDecimals(double[] vector) {
		if (vector == null) {
			return List.of();
		}
		List<BigDecimal> values = new ArrayList<>(vector.length);
		for (double value : vector) {
			values.add(BigDecimal.valueOf(value));
		}
		return values;
	}

	private CdsVector toVector(double[] vector) {
		if (vector == null || vector.length == 0) {
			return null;
		}
		float[] values = new float[vector.length];
		for (int i = 0; i < vector.length; i++) {
			values[i] = (float) vector[i];
		}
		return CdsVector.of(values);
	}

	private String asString(Map<String, Object> row, String key) {
		Object value = row.get(key);
		return value == null ? null : value.toString();
	}

	private int defaultInt(Number value) {
		return value == null ? 0 : value.intValue();
	}

	private double toDouble(Number value) {
		return value == null ? 0.0 : value.doubleValue();
	}

	public record ChunkPersistRequest(BookTextChunk chunk, double[] embedding) {
	}
}
