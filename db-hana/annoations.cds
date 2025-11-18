namespace my.bookshop;
using my.bookshop.BookContentChunks from '../db/book-chunks';

extend entity BookContentChunks with {
    embedding : Vector(1536);
};
