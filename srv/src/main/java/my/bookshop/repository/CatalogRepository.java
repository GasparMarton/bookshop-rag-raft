package my.bookshop.repository;

import static cds.gen.catalogservice.CatalogService_.BOOKS;

import cds.gen.catalogservice.Authors_;
import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.Books_;
import cds.gen.catalogservice.ChatResultBook;
import cds.gen.catalogservice.Reviews;
import cds.gen.my.bookshop.Authors;
import cds.gen.reviewservice.ReviewService;
import cds.gen.reviewservice.ReviewService_;
import com.sap.cds.CdsVector;
import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.reflect.CdsBaseType;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

	private final PersistenceService db;
	private final ReviewService reviewService;

	@Autowired
	public CatalogRepository(PersistenceService db, ReviewService reviewService) {
		this.db = db;
		this.reviewService = reviewService;
	}

	public boolean hasReviewFromUser(Books_ ref, String user) {
		return db.run(Select.from(ref.reviews())
				.where(review -> review.createdBy().eq(user))).first().isPresent();
	}

	public Set<String> findReviewedBooksByUser(List<String> bookIds, String user) {
		if (bookIds == null || bookIds.isEmpty()) {
			return Collections.emptySet();
		}
		var query = Select.from(BOOKS, b -> b.filter(b.ID().in(bookIds)).reviews())
				.where(r -> r.createdBy().eq(user));
		return db.run(query).stream().map(Reviews::getBookId).collect(Collectors.toSet());
	}

	public Books fetchBook(String bookId) {
		return db.run(Select.from(BOOKS).columns(Books_::stock).byId(bookId)).single();
	}

	public void updateBookStock(String bookId, int newStock) {
		db.run(Update.entity(BOOKS).byId(bookId).data(Books.STOCK, newStock));
	}

	public Reviews insertReview(cds.gen.reviewservice.Reviews review) {
		Result res = reviewService.run(Insert.into(ReviewService_.REVIEWS).entry(review));
		return res.single(Reviews.class);
	}

	public List<cds.gen.my.bookshop.Books> findSimilarBooks(CdsVector vector) {
		if (vector == null) {
			return List.of();
		}
		var similarity = CQL.cosineSimilarity(CQL.get(cds.gen.my.bookshop.Books.EMBEDDING), CQL.param(0).type(CdsBaseType.VECTOR));
		var select = Select.from(cds.gen.my.bookshop.Books_.class)
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
		return db.run(select).listOf(cds.gen.my.bookshop.Books.class);
	}

    public List<cds.gen.my.bookshop.Books> getByIds(List<String> ids) {
		var select = Select.from(Books_.class)
			.where(b-> b.ID().in(ids));
        return db.run(select).listOf(cds.gen.my.bookshop.Books.class);
    }
}
