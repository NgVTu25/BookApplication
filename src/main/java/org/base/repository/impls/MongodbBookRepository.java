package org.base.repository.impls;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.base.util.MongoUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class MongodbBookRepository implements BookRepository {
    private final MongoCollection<Document> collection = MongoUtil.getCollection("books");

    @Override
    public void save(Book book) {
        Document doc = mapToDocument(book);
        book.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        collection.insertOne(doc);
    }

    @Override
    public void update(Long id,Book book) {
        Document doc = mapToDocument(book);

        collection.replaceOne(Filters.eq("_id", book.getId()), doc);
    }

    @Override
    public Page<Book> search(String title, String author, String content, Pageable pageable) {
        List<Bson> filters = new ArrayList<>();

        if (title != null && !title.isEmpty()) {
            filters.add(Filters.regex("title", Pattern.compile(title, Pattern.CASE_INSENSITIVE)));
        }
        if (author != null && !author.isEmpty()) {
            filters.add(Filters.regex("author", Pattern.compile(author, Pattern.CASE_INSENSITIVE)));
        }
        if (content != null && !content.isEmpty()) {
            filters.add(Filters.regex("content", Pattern.compile(content, Pattern.CASE_INSENSITIVE)));
        }

        Bson finalQuery = filters.isEmpty() ? new Document() : Filters.and(filters);

        List<Book> result = new ArrayList<>();
        for (Document doc : collection.find(finalQuery)) {
            result.add(mapToBook(doc));
        }
        return new PageImpl<>(result, pageable, result.size());
    }

    @Override
    public void deleteByIds(List<String> ids) {
        List<Long> cleanId = ids.stream().map(String::trim).map(Long::parseLong).toList();
        if (ids != null && !ids.isEmpty()) {
            collection.deleteMany(Filters.in("_id", cleanId));
        }
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        List<Book> books = new ArrayList<>();
        for (Document doc : collection.find().skip((int) pageable.getOffset()).limit(pageable.getPageSize())) {
            books.add(mapToBook(doc));
        }
        long total = collection.countDocuments();
        return new PageImpl<>(books, pageable, total);
    }

    @Override
    public void saveAll(List<Book> books) {
        if (books == null || books.isEmpty()) return;

        List<Document> documents = new ArrayList<>();
        for (Book book : books) {
            book.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
            documents.add(mapToDocument(book));
        }

        collection.insertMany(documents);
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        Map<String, Object> result = new HashMap<>();

        long totalBooks = collection.countDocuments(Filters.eq("author", author));
        result.put("Tổng số sách", totalBooks);

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.eq("author", author)),
                Aggregates.group("$category", Accumulators.sum("count", 1))
        );

        Map<String, Integer> categoryStats = new HashMap<>();
        for (Document doc : collection.aggregate(pipeline)) {
            String category = doc.getString("_id");
            Integer count = doc.getInteger("count");
            categoryStats.put(category, count);
        }

        result.put("Thống kê theo thể loại", categoryStats);
        return result;
    }

    private Document mapToDocument(Book book) {
        Object finalId = (book.getId() == null) ? UUID.randomUUID().toString() : book.getId();

        return new Document("_id", finalId)
                .append("author", book.getAuthor())
                .append("category", book.getCategory())
                .append("title", book.getTitle())
                .append("content", book.getContent())
                .append("createDate", book.getCreateDate() != null ? book.getCreateDate().toString() : null)
                .append("viewCount", book.getViewCount())
                .append("downloadCount", book.getDownloadCount());
    }

    private Book mapToBook(Document doc) {
        Book book = new Book();

        Object idObj = doc.get("_id");
        if (idObj instanceof Number) {
            book.setId(((Number) idObj).longValue());
        }

        book.setAuthor(doc.getString("author"));
        book.setCategory(doc.getString("category"));
        book.setContent(doc.getString("content"));

        return book;
    }

    private String getOrDefault(String newVal, String oldVal) {
        return (newVal == null || newVal.trim().isEmpty()) ? oldVal : newVal;
    }

    private Long getOrDefault(Long newVal, Long oldVal) {
        return (newVal == null) ? oldVal : newVal;
    }

}