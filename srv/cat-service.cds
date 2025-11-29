using {my.bookshop as my} from '../db/index';

@path : 'browse'
@odata.apply.transformations
service CatalogService @(requires: 'any') {
    @readonly
    @cds.redirection.target
    entity Books       as projection on my.Books excluding {
        createdBy,
        modifiedBy,
        fullText,
        chunks
    } actions {
        action addReview(rating : Integer, title : String, text : String) returns Reviews;
    };

    @readonly
    entity Authors     as projection on my.Authors;

    @readonly
    entity Reviews     as projection on my.Reviews;
    @readonly
    entity GenreHierarchy as projection on my.Genres;

    action submitOrder(book : Books : ID, quantity : Integer) returns {
        stock : Integer
    };

    type ChatResultBook {
        ID             : UUID;
        title          : localized String(111);
        descr          : localized String(1111);
        author_ID      : UUID;
        author_name    : String(111);
        genre_ID       : UUID;
        genre_name     : String(255);
        stock          : Integer;
        price          : Decimal(9, 2);
        currency_code  : String(3);
        rating         : Decimal(2, 1);
    }

    type ChatResult : {
        reply : String;
        books : many ChatResultBook;
        needsVectorSearch : Boolean;
    };
    
    // Conversational action: always returns text; may also return matching books
    // 'history' is a JSON string of [{ role: 'user'|'assistant', content: String }]
    action chat(message : String, history : String) returns ChatResult;

    // access control restrictions
    annotate Reviews with @restrict : [
        {
            grant : 'READ',
            to : 'any'
        },
        {
            grant : 'CREATE',
            to : 'authenticated-user'
        }
    ];
}
