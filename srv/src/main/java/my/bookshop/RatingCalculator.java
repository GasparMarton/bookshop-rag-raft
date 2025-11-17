package my.bookshop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;
import java.util.stream.Stream;
import my.bookshop.repository.bookshop.BookshopBooksRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Takes care of calculating the average rating of a book based on its review
 * ratings.
 */
@Component
public class RatingCalculator {

	@Autowired
	private BookshopBooksRepository bookshopBooksRepository;

	/**
	 * Initializes the ratings for all existing books based on their reviews.
	 */
	public void initBookRatings() {
		bookshopBooksRepository.findAllBookIds().forEach(this::setBookRating);
	}

	/**
	 * Sets the average rating for the given book.
	 *
	 * @param bookId
	 */
	public void setBookRating(String bookId) {
		var reviews = bookshopBooksRepository.findReviewsForBook(bookId);
		Stream<Double> ratings = reviews.stream().map(r -> r.getRating().doubleValue());
		BigDecimal rating = getAvgRating(ratings);

		bookshopBooksRepository.updateBookRating(bookId, rating);
	}

	static BigDecimal getAvgRating(Stream<Double> ratings) {
		OptionalDouble avg = ratings.mapToDouble(Double::doubleValue).average();
		if (!avg.isPresent()) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(avg.getAsDouble()).setScale(1, RoundingMode.HALF_UP);
	}
}
