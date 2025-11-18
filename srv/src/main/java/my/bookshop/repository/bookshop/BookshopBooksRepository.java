package my.bookshop.repository.bookshop;

import static cds.gen.my.bookshop.Bookshop_.BOOKS;

import cds.gen.my.bookshop.Books;
import cds.gen.my.bookshop.Reviews;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.Upsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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

	public Map<String, Books> findSummariesByIds(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		List<String> normalized = ids.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(id -> !id.isEmpty())
				.distinct()
				.toList();
		if (normalized.isEmpty()) {
			return Map.of();
		}
		CqnSelect select = Select.from(BOOKS)
				.columns(b -> b.ID(), b -> b.title(), b -> b.descr())
				.where(b -> b.ID().in(normalized.toArray(String[]::new)));
		return db.run(select)
				.streamOf(Books.class)
				.collect(Collectors.toMap(Books::getId, Function.identity(), (left, right) -> left));
	}
}
