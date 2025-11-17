package my.bookshop.repository.adminservice;

import static cds.gen.adminservice.AdminService_.ORDERS;

import cds.gen.adminservice.Orders;
import cds.gen.adminservice.Orders_;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class OrdersRepository {

	@Autowired
	private PersistenceService db;

	public Optional<Orders> findOrderCurrencyForActiveEntity(String ordersId) {
		CqnSelect select = Select.from(ORDERS).columns(Orders_::OrderNo, Orders_::currency_code)
				.where(o -> o.ID().eq(ordersId).and(o.IsActiveEntity().eq(true)));
		return db.run(select).first(Orders.class);
	}

	public Optional<Orders> findOrder(CqnSelect select) {
		return db.run(select).first(Orders.class);
	}
}
