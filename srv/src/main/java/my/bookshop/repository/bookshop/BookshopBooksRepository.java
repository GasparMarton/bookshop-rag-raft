package my.bookshop.repository.bookshop;

import static cds.gen.my.bookshop.Bookshop_.BOOKS;

import cds.gen.my.bookshop.Books;
import cds.gen.my.bookshop.Reviews;
import com.sap.cds.CdsVector;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.Upsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BookshopBooksRepository {

	@Autowired
	private PersistenceService db;

	public List<String> findAllBookIds() {
		CqnSelect select = Select.from(BOOKS).columns(b -> b.ID());
		return db.run(select)
				.streamOf(Books.class)
				.map(Books::getId)
				.collect(Collectors.toList());
	}

	public List<Reviews> findReviewsForBook(String bookId) {
		CqnSelect select = Select.from(BOOKS, b -> b.filter(b.ID().eq(bookId)).reviews());
		return db.run(select).listOf(Reviews.class);
	}

	public List<Books> findAllWithTextFields() {
		CqnSelect select = Select.from(BOOKS)
				.columns(b -> b.ID(), b -> b.title(), b -> b.descr(), b -> b.fullText());
		return db.run(select).listOf(Books.class);
	}

	public Optional<Books> findByIdWithTextFields(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return Optional.empty();
		}
		CqnSelect select = Select.from(BOOKS)
				.columns(b -> b.ID(), b -> b.title(), b -> b.descr(), b -> b.fullText())
				.byId(bookId);
		return db.run(select).first(Books.class);
	}

	public void updateBookRating(String bookId, BigDecimal rating) {
		db.run(Update.entity(BOOKS).byId(bookId).data(Books.RATING, rating));
	}

	public Optional<Books> findBookStock(String bookId) {
		CqnSelect select = Select.from(BOOKS).columns(b -> b.ID(), b -> b.stock()).byId(bookId);
		return db.run(select).first(Books.class);
	}

	public Optional<Books> findBookStockAndPrice(String bookId) {
		CqnSelect select = Select.from(BOOKS).columns(b -> b.ID(), b -> b.stock(), b -> b.price()).byId(bookId);
		return db.run(select).first(Books.class);
	}

	public Optional<BigDecimal> findBookPrice(String bookId) {
		CqnSelect select = Select.from(BOOKS).byId(bookId).columns(b -> b.price());
		return db.run(select).first(Books.class).map(Books::getPrice);
	}

	public void updateBook(Books book) {
		db.run(Update.entity(BOOKS).data(book));
	}

	public void upsertBook(Books book) {
		db.run(Upsert.into(BOOKS).entry(book));
	}

	public void updateEmbedding(String bookId, double[] vector) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		List<BigDecimal> values = toBigDecimals(vector);
		db.run(Update.entity(BOOKS).byId(bookId)
				.data(Books.EMBEDDING, values));
	}

	public void clearEmbedding(String bookId) {
		if (bookId == null || bookId.isBlank()) {
			return;
		}
		db.run(Update.entity(BOOKS).byId(bookId)
				.data(Books.EMBEDDING, null));
	}

	public void clearAllEmbeddings() {
		db.run(Update.entity(BOOKS)
				.data(Books.EMBEDDING, null));
	}

	public List<Books> findSimilarBooksByVector(double[] vector) {
		CdsVector cdsVector = toVector(vector);
		if (cdsVector == null) {
			return List.of();
		}
		var similarity = CQL.cosineSimilarity(CQL.get(Books.EMBEDDING), CQL.val(cdsVector));
		CqnSelect select = Select.from(BOOKS)
				.columns(b -> b.ID(),
						b -> b.title(),
						b -> b.descr(),
						b -> b.stock(),
						b -> b.price(),
						b -> b.currency_code(),
						b -> b.rating(),
						b -> similarity.as("similarity"))
					.where(b -> b.embedding().isNotNull())
					.orderBy(b -> b.get("similarity").desc())
					.limit(10);
		return db.run(select).listOf(Books.class);
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
}
