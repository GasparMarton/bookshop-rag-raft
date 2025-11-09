using CatalogService from './cat-service';

service RagService {
    action rebuildAll();
    action reindexBook(book : CatalogService.Books:ID);
}

