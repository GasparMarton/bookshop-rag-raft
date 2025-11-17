package my.bookshop.repository.bookshop;

import cds.gen.my.bookshop.Bookshop_;
import cds.gen.my.bookshop.OrderItems;
import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class OrderItemsRepository {

	@Autowired
	private PersistenceService db;

	public Optional<OrderItemSnapshot> findQuantityAndBook(String orderItemId) {
		if (orderItemId == null) {
			return Optional.empty();
		}
		CqnSelect select = Select.from(Bookshop_.ORDER_ITEMS)
				.columns(i -> i.quantity(), i -> i.book_ID())
				.byId(orderItemId);
		Result result = db.run(select);
		return result.first(OrderItems.class)
				.map(item -> new OrderItemSnapshot(item.getQuantity(), item.getBookId()));
	}

	public static class OrderItemSnapshot {
		private final Integer quantity;
		private final String bookId;

		public OrderItemSnapshot(Integer quantity, String bookId) {
			this.quantity = quantity;
			this.bookId = bookId;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public String getBookId() {
			return bookId;
		}
	}
}
