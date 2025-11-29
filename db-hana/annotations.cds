namespace my.bookshop;
using my.bookshop.BookChunks from '../db/book-chunks';

extend entity BookChunks with {
    embedding : Vector(1536);
};
