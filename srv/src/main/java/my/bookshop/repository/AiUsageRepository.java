package my.bookshop.repository;

import cds.gen.my.bookshop.AiUsageRecords;
import cds.gen.my.bookshop.AiUsageRecords_;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class AiUsageRepository {

	private static final Logger logger = LoggerFactory.getLogger(AiUsageRepository.class);

	private final PersistenceService db;

	public AiUsageRepository(PersistenceService db) {
		this.db = db;
	}

	public void saveRecord(String modelName, int inputTokens, int outputTokens, int totalTokens) {
		if (modelName == null || modelName.isBlank()) {
			return;
		}
		AiUsageRecords entity = AiUsageRecords.create();
		entity.setId(UUID.randomUUID().toString());
		entity.setModel(modelName);
		entity.setInputTokens(inputTokens);
		entity.setOutputTokens(outputTokens);
		entity.setTotalTokens(totalTokens);

		try {
			db.run(Insert.into(AiUsageRecords_.CDS_NAME).entry(entity));
		} catch (Exception e) {
			logger.warn("Failed to persist AI usage record for model {}", modelName, e);
		}
	}
}
