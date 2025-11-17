package my.bookshop.handlers;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.Books;
import cds.gen.adminservice.Books_;
import cds.gen.adminservice.RebuildEmbeddingsContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.List;
import java.util.function.Consumer;
import my.bookshop.rag.BookEmbeddingService;
import org.springframework.stereotype.Component;

@Component
@ServiceName(AdminService_.CDS_NAME)
class AdminBookEmbeddingHandler implements EventHandler {

	private final BookEmbeddingService embeddingService;

	AdminBookEmbeddingHandler(BookEmbeddingService embeddingService) {
		this.embeddingService = embeddingService;
	}

	@After(event = CqnService.EVENT_CREATE, entity = Books_.CDS_NAME)
	public void afterCreateBooks(List<Books> books) {
		reindexBooks(books);
	}

	@After(event = CqnService.EVENT_UPDATE, entity = Books_.CDS_NAME)
	public void afterUpdateBooks(List<Books> books) {
		reindexBooks(books);
	}

	@After(event = CqnService.EVENT_DELETE, entity = Books_.CDS_NAME)
	public void afterDeleteBooks(List<Books> books) {
		deleteEmbeddings(books);
	}

	@On
	public void rebuildEmbeddings(RebuildEmbeddingsContext context) {
		embeddingService.deleteAllEmbeddings();
		embeddingService.rebuildAll();
	}

	private void reindexBooks(List<Books> books) {
		forEachValidBookId(books, embeddingService::reindexBook);
	}

	private void deleteEmbeddings(List<Books> books) {
		forEachValidBookId(books, embeddingService::deleteEmbedding);
	}

	private void forEachValidBookId(List<Books> books, Consumer<String> action) {
		if (books == null || action == null) {
			return;
		}
		books.stream()
				.map(Books::getId)
				.filter(id -> id != null && !id.isBlank())
				.forEach(action);
	}
}
