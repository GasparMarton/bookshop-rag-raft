package my.bookshop.rag;

import cds.gen.ragservice.RagService_;
import cds.gen.ragservice.RebuildAllContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.springframework.stereotype.Component;

@Component
@ServiceName(RagService_.CDS_NAME)
public class RagServiceHandler implements EventHandler {

	private final BookEmbeddingService embeddingService;

	public RagServiceHandler(BookEmbeddingService embeddingService) {
		this.embeddingService = embeddingService;
	}

	@On
	public void rebuildAll(RebuildAllContext context) {
		embeddingService.rebuildAll();
	}

}
