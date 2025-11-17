package my.bookshop.repository.adminservice;

import static cds.gen.adminservice.AdminService_.GENRE_HIERARCHY;

import cds.gen.adminservice.GenreHierarchy;
import cds.gen.adminservice.GenreHierarchy_;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class GenreHierarchyRepository {

	@Autowired
	private PersistenceService db;

	public GenreHierarchy loadNode(GenreHierarchy_ ref) {
		CqnSelect select = Select.from(ref)
				.columns(c -> c.ID(), c -> c.parent_ID());
		return db.run(select).single(GenreHierarchy.class);
	}

	public List<GenreHierarchy> findSiblings(String parentId) {
		CqnSelect select = Select.from(GENRE_HIERARCHY)
				.columns(c -> c.ID(), c -> c.siblingRank())
				.where(c -> c.parent_ID().eq(parentId));
		return db.run(select).listOf(GenreHierarchy.class);
	}

	public void updateSiblings(List<GenreHierarchy> siblings) {
		db.run(Update.entity(GENRE_HIERARCHY).entries(siblings));
	}
}
