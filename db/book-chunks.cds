namespace my.bookshop;

using { cuid, managed } from '@sap/cds/common';
using my.bookshop.Books from './books';

/**
 * Stores individual semantic chunks of book content along with their metadata.
 */
entity BookChunks : cuid, managed {
    book       : Association to Books;
    chunkIndex : Integer;
    source     : String(40);
    text       : LargeString;
}

extend Books with {
    chunks : Composition of many BookChunks
        on chunks.book = $self;
};
