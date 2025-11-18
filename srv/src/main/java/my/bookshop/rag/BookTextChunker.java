package my.bookshop.rag;

import cds.gen.my.bookshop.Books;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits book texts into overlapping character-based chunks so we can create
 * multiple embeddings per book.
 */
public class BookTextChunker {

	private static final int DEFAULT_CHUNK_SIZE = 900;
	private static final int DEFAULT_CHUNK_OVERLAP = 150;

	private final int chunkSize;
	private final int chunkOverlap;

	public BookTextChunker() {
		this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
	}

	public BookTextChunker(int chunkSize, int chunkOverlap) {
		this.chunkSize = Math.max(200, chunkSize);
		this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize - 1));
	}

	public List<BookTextChunk> chunk(Books book) {
		if (book == null) {
			return List.of();
		}
		List<BookTextChunk> chunks = new ArrayList<>();
		int index = 0;
		index = appendSection(chunks, index, BookChunkSource.TITLE, book.getTitle());
		index = appendSection(chunks, index, BookChunkSource.DESCRIPTION, book.getDescr());
		appendSectionReader(chunks, index, BookChunkSource.BODY, book.getFullText());
		return chunks;
	}

	private int appendSection(List<BookTextChunk> accumulator, int index, BookChunkSource source, String raw) {
		String text = raw.toString();
		if (text.isEmpty()) {
			return index;
		}
		for (String chunkText : split(text)) {
			String normalizedChunk = normalize(chunkText);
			if (normalizedChunk.isEmpty()) {
				continue;
			}
			accumulator.add(new BookTextChunk(index++, source, normalizedChunk));
		}
		return index;
	}

	private int appendSectionReader(List<BookTextChunk> accumulator, int index, BookChunkSource source, Reader raw) {
		String text = raw != null ? raw.toString() : "";
		if (text.isEmpty()) {
			return index;
		}
		for (String chunkText : split(text)) {
			String normalizedChunk = normalize(chunkText);
			if (normalizedChunk.isEmpty()) {
				continue;
			}
			accumulator.add(new BookTextChunk(index++, source, normalizedChunk));
		}
		return index;
	}

	private String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text.replaceAll("\\s+", " ").trim();
	}

	private List<String> split(String text) {
		if (text.length() <= chunkSize) {
			return List.of(text);
		}
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int preferredEnd = Math.min(text.length(), start + chunkSize);
			int end = preferredEnd;
			if (preferredEnd < text.length()) {
				end = findBoundary(text, start, preferredEnd);
			}
			String piece = text.substring(start, Math.max(start + 1, end));
			chunks.add(piece);
			if (end >= text.length()) {
				break;
			}
			int nextStart = end - chunkOverlap;
			start = Math.max(nextStart, start + 1);
		}
		return chunks.stream()
				.map(this::normalize)
				.filter(chunk -> !chunk.isEmpty())
				.toList();
	}

	private int findBoundary(String text, int start, int preferredEnd) {
		int boundarySearchStart = Math.max(start, preferredEnd - 200);
		for (int i = preferredEnd; i > boundarySearchStart; i--) {
			char ch = text.charAt(i - 1);
			if (isBoundaryCharacter(ch)) {
				return i;
			}
		}
		int spaceIndex = text.lastIndexOf(' ', preferredEnd - 1);
		if (spaceIndex > start + 50) {
			return spaceIndex;
		}
		return preferredEnd;
	}

	private boolean isBoundaryCharacter(char ch) {
		return ch == '.' || ch == '!' || ch == '?' || ch == ';' || ch == ':' || ch == '\n';
	}
}
