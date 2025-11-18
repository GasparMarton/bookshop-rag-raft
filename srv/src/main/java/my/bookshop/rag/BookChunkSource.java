package my.bookshop.rag;

import java.util.Locale;

/**
 * Identifies which part of the book a chunk originated from.
 */
public enum BookChunkSource {
	TITLE,
	DESCRIPTION,
	BODY;

	public static BookChunkSource from(String raw) {
		if (raw == null || raw.isBlank()) {
			return BODY;
		}
		try {
			return BookChunkSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return BODY;
		}
	}
}

