using { my.bookshop as my } from '../db/index';

@path: 'seeding'
service SeedingService {
    entity Books as projection on my.Books;
    entity BookChunks as projection on my.BookChunks excluding { embedding };
}
