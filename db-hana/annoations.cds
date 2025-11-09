namespace my.bookshop;
using my.bookshop.Books from '../db/books';

extend entity Books with {
    embedding : Vector(1536);
};
