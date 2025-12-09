package my.bookshop.handlers;

import cds.gen.catalogservice.Books;
import cds.gen.catalogservice.BooksAddReviewContext;
import cds.gen.catalogservice.Books_;
import cds.gen.catalogservice.CatalogService_;
import cds.gen.catalogservice.ChatContext;
import cds.gen.catalogservice.ChatFtContext;
import cds.gen.catalogservice.ChatResult;
import cds.gen.catalogservice.Reviews;
import cds.gen.catalogservice.SubmitOrderContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.List;
import java.util.stream.Stream;
import my.bookshop.service.CatalogBusinessService;
import org.springframework.stereotype.Component;

@Component
@ServiceName(CatalogService_.CDS_NAME)
class CatalogServiceHandler implements EventHandler {

	private final CatalogBusinessService catalogService;

	CatalogServiceHandler(CatalogBusinessService catalogService) {
		this.catalogService = catalogService;
	}

	@Before(entity = Books_.CDS_NAME)
	public void beforeReadBooks(CdsReadEventContext context) {
		catalogService.ensureStockColumn(context);
	}

	@Before
	public void beforeAddReview(Books_ ref, BooksAddReviewContext context) {
		catalogService.validateBeforeAddReview(ref, context);
	}

	@On
	public Reviews onAddReview(Books_ ref, BooksAddReviewContext context) {
		return catalogService.addReview(ref, context);
	}

	@After(entity = Books_.CDS_NAME)
	public void afterAddReview(BooksAddReviewContext context) {
		catalogService.updateRatingAfterReview(context);
	}

	@After(event = CqnService.EVENT_READ)
	public void discountBooks(Stream<Books> books) {
		catalogService.applyDiscounts(books);
	}

	@After
	public void setIsReviewable(CdsReadEventContext context, List<Books> books) {
		catalogService.determineReviewable(context, books);
	}

	@After(event = CqnService.EVENT_CREATE, entity = Books_.CDS_NAME)
	public void afterCreateBooks(List<Books> books) {
		catalogService.handleBookCreatedOrUpdated(books);
	}

	@After(event = CqnService.EVENT_UPDATE, entity = Books_.CDS_NAME)
	public void afterUpdateBooks(List<Books> books) {
		catalogService.handleBookCreatedOrUpdated(books);
	}

	@After(event = CqnService.EVENT_DELETE, entity = Books_.CDS_NAME)
	public void afterDeleteBooks(List<Books> books) {
		catalogService.handleBookDeleted(books);
	}

	@On
	public SubmitOrderContext.ReturnType onSubmitOrder(SubmitOrderContext context) {
		return catalogService.submitOrder(context);
	}

	@On
	public void onChat(ChatContext context) {
		ChatResult result = catalogService.handleChat(context);
		context.setResult(result);
	}

	@On
	public void onChatFt(ChatFtContext context) {
		ChatResult result = catalogService.handleChatFt(context);
		context.setResult(result);
	}
}
