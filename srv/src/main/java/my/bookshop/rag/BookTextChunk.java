package my.bookshop.rag;

/**
 * Represents a normalized chunk of book text that is ready for embedding.
 */
public record BookTextChunk(int index, BookChunkSource source, String text) {
}

