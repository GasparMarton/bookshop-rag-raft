package my.bookshop.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import my.bookshop.repository.bookshop.BookshopBooksRepository;

@Service
public class RagRetrievalService {

	private final RagAiClient aiClient;
	private final BookshopBooksRepository bookshopBooksRepository;

	public RagRetrievalService(RagAiClient aiClient, BookshopBooksRepository bookshopBooksRepository) {
		this.aiClient = aiClient;
		this.bookshopBooksRepository = bookshopBooksRepository;
	}

	public double[] embedForQuery(String text) {
		return aiClient.embed(text);
	}

	public List<TextSegment> similaritySearch(double[] vector) {
		List<cds.gen.my.bookshop.Books> rows = bookshopBooksRepository.findSimilarBooksByVector(vector);
		List<TextSegment> contexts = new ArrayList<>();
		for (cds.gen.my.bookshop.Books row : rows) {
			String id = row.getId();
			String title = row.getTitle();
			String descr = row.getDescr();
			Object similarityRaw = row.get("similarity");
			double similarity = similarityRaw instanceof Number ? ((Number) similarityRaw).doubleValue() : 0.0;
			contexts.add(toSegment(id, title, descr, similarity));
		}
		return contexts;
	}

	private TextSegment toSegment(String id, String title, String descr, double similarity) {
		StringBuilder text = new StringBuilder();
		if (title != null && !title.isBlank()) {
			text.append(title);
		}
		if (descr != null && !descr.isBlank()) {
			if (text.length() > 0) {
				text.append(" — ");
			}
			text.append(descr);
		}
		if (text.length() == 0) {
			text.append("No description available.");
		}
		Metadata metadata = new Metadata();
		if (id != null) {
			metadata.put("bookId", id);
		}
		if (title != null) {
			metadata.put("title", title);
		}
		metadata.put("similarity", similarity);
		if (descr != null) {
			metadata.put("excerpt", descr);
		}
		return TextSegment.from(text.toString(), metadata);
	}
}
