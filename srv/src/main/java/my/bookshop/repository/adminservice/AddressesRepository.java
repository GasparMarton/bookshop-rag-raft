package my.bookshop.repository.adminservice;

import static cds.gen.adminservice.AdminService_.ADDRESSES;

import cds.gen.adminservice.Addresses;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Upsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AddressesRepository {

	@Autowired
	private PersistenceService db;

	public Result runSelect(CqnSelect select) {
		return db.run(select);
	}

	public Result findReplica(String businessPartner, String addressId) {
		CqnSelect select = Select.from(ADDRESSES)
				.where(a -> a.businessPartner().eq(businessPartner).and(a.ID().eq(addressId)));
		return db.run(select);
	}

	public void insertAddress(Addresses address) {
		db.run(Insert.into(ADDRESSES).entry(address));
	}

	public Result findReplicas(String businessPartner) {
		CqnSelect select = Select.from(ADDRESSES).where(a -> a.businessPartner().eq(businessPartner));
		return db.run(select);
	}

	public void upsertAddresses(List<Addresses> replicas) {
		db.run(Upsert.into(ADDRESSES).entries(replicas));
	}
}
