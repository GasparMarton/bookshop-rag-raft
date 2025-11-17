package my.bookshop.repository;

import static cds.gen.catalogservice.CatalogService_.BOOKS;

import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.Books_;
import cds.gen.catalogservice.ChatResultBook;
import cds.gen.catalogservice.Reviews;
import cds.gen.reviewservice.ReviewService;
import cds.gen.reviewservice.ReviewService_;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

	@Autowired
	private PersistenceService db;

	@Autowired
	private ReviewService reviewService;

	public boolean hasReviewFromUser(Books_ ref, String user) {
		CqnSelect select = Select.from(ref.reviews())
				.where(review -> review.createdBy().eq(user));
		Result result = db.run(select);
		return result.first(Reviews.class).isPresent();
	}

	public Set<String> findReviewedBooksByUser(List<String> bookIds, String user) {
		List<String> normalizedIds = normalizeIds(bookIds);
		if (normalizedIds.isEmpty()) {
			return Collections.emptySet();
		}
		CqnSelect select = Select.from(BOOKS, b -> b.filter(b.ID().in(normalizedIds)).reviews())
				.where(r -> r.createdBy().eq(user));
		Result result = db.run(select);
		return result.streamOf(Reviews.class).map(Reviews::getBookId).collect(Collectors.toSet());
	}

	public Books fetchBook(String bookId) {
		CqnSelect select = Select.from(BOOKS).columns(Books_::stock).byId(bookId);
		return db.run(select).single(Books.class);
	}

	public void updateBookStock(String bookId, int newStock) {
		db.run(Update.entity(BOOKS).byId(bookId).data(Books.STOCK, newStock));
	}

	public Reviews insertReview(cds.gen.reviewservice.Reviews review) {
		Result res = db.run(Insert.into(ReviewService_.REVIEWS).entry(review));
		return res.single(Reviews.class);
	}

	public List<ChatResultBook> getChatResultBooks(List<String> ids) {
		List<String> normalizedIds = normalizeIds(ids);
		if (normalizedIds.isEmpty()) {
			return List.of();
		}
		CqnSelect select = Select.from(BOOKS)
				.columns(b -> b.ID(),
						b -> b.title(),
						b -> b.descr(),
						b -> b.author_ID(),
						b -> b.author().name().as(ChatResultBook.AUTHOR_NAME),
						b -> b.genre_ID(),
						b -> b.genre().name().as(ChatResultBook.GENRE_NAME),
						b -> b.stock(),
						b -> b.price(),
						b -> b.currency_code(),
						b -> b.rating())
				.where(b -> b.ID().in(normalizedIds));
		List<ChatResultBook> results = db.run(select).listOf(ChatResultBook.class);
		if (results.isEmpty()) {
			return results;
		}
		Map<String, Integer> ordering = indexById(normalizedIds);
		results.sort(Comparator.comparing(book -> ordering.getOrDefault(book.getId(), Integer.MAX_VALUE)));
		return results;
	}

	private List<String> normalizeIds(List<String> ids) {
		if (ids == null) {
			return List.of();
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				unique.add(id.trim());
			}
		}
		return List.copyOf(unique);
	}

	private Map<String, Integer> indexById(List<String> ids) {
		Map<String, Integer> ordering = new HashMap<>();
		for (int i = 0; i < ids.size(); i++) {
			ordering.put(ids.get(i), i);
		}
		return ordering;
	}
}
