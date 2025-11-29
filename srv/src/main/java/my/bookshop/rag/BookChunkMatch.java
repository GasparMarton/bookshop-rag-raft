package my.bookshop.rag;

/**
 * Holds the similarity result for a chunk that matched a query vector.
 */
public record BookChunkMatch(
		String chunkId,
		String bookId,
		int chunkIndex,
		BookChunkSource source,
		String text,
		double similarity) {
}
