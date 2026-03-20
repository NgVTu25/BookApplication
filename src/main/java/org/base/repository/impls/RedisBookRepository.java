package org.base.repository.impls;

import com.google.gson.*;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.base.util.RedisUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RedisBookRepository implements BookRepository {

    private static final String BOOKS_ALL_KEY = "books:all";

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                            new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                            LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    private String bookKey(Long id) {
        return "book:" + id;
    }

    private String authorBooksKey(String author) {
        return "author:" + normalize(author) + ":books";
    }

    private String authorStatsKey(String author) {
        return "stats:author:" + normalize(author);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Long generateId() {
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    private void addIndexes(Jedis jedis, Book book) {
        if (book == null || book.getId() == null) return;

        String id = String.valueOf(book.getId());

        jedis.sadd(authorBooksKey(book.getAuthor()), id);
        jedis.hincrBy(authorStatsKey(book.getAuthor()), "total", 1);

        if (book.getCategory() != null && !book.getCategory().trim().isEmpty()) {
            jedis.hincrBy(authorStatsKey(book.getAuthor()), "category:" + book.getCategory(), 1);
        }
    }

    private void removeIndexes(Jedis jedis, Book book) {
        if (book == null || book.getId() == null) return;

        String id = String.valueOf(book.getId());

        jedis.srem(authorBooksKey(book.getAuthor()), id);
        jedis.hincrBy(authorStatsKey(book.getAuthor()), "total", -1);

        if (book.getCategory() != null && !book.getCategory().trim().isEmpty()) {
            jedis.hincrBy(authorStatsKey(book.getAuthor()), "category:" + book.getCategory(), -1);
        }
    }

    @Override
    public void save(Book book) {
        if (book == null) return;

        if (book.getId() == null) {
            book.setId(generateId());
        }

        try (Jedis jedis = RedisUtil.getConnection()) {
            String key = bookKey(book.getId());

            if (jedis.exists(key)) {
                System.out.println("Book với ID " + book.getId() + " đã tồn tại!");
                return;
            }

            String json = gson.toJson(book);
            jedis.set(key, json);

            jedis.rpush(BOOKS_ALL_KEY, String.valueOf(book.getId()));
            addIndexes(jedis, book);
        }
    }

    @Override
    public void update(Long id, Book book) {
        if (id == null || book == null) return;

        try (Jedis jedis = RedisUtil.getConnection()) {
            String key = bookKey(id);
            String oldJson = jedis.get(key);

            if (oldJson == null || oldJson.isEmpty()) {
                System.out.println("Không tìm thấy sách với ID: " + id + " để cập nhật!");
                return;
            }

            Book oldBook = gson.fromJson(oldJson, Book.class);

            book.setId(id);

            removeIndexes(jedis, oldBook);

            String newJson = gson.toJson(book);
            jedis.set(key, newJson);

            addIndexes(jedis, book);

            System.out.println("Cập nhật thành công sách ID: " + id);
        }
    }

    @Override
    public Page<Book> search(String name, String author, String content, Pageable pageable) {
        List<Book> result = new ArrayList<>();

        try (Jedis jedis = RedisUtil.getConnection()) {
            if (author != null && !author.trim().isEmpty()) {
                Set<String> bookIds = jedis.smembers(authorBooksKey(author));

                if (!bookIds.isEmpty()) {
                    String[] keys = bookIds.stream()
                            .map(id -> "book:" + id)
                            .toArray(String[]::new);

                    List<String> jsons = jedis.mget(keys);

                    for (String json : jsons) {
                        if (json != null) {
                            Book book = gson.fromJson(json, Book.class);

                            boolean matchName = (name == null || name.trim().isEmpty())
                                    || (book.getTitle() != null && book.getTitle().toLowerCase().contains(name.trim().toLowerCase()));

                            boolean matchContent = (content == null || content.trim().isEmpty())
                                    || (book.getContent() != null && book.getContent().toLowerCase().contains(content.trim().toLowerCase()));

                            if (matchName && matchContent) {
                                result.add(book);
                            }
                        }
                    }
                }
            } else {
                List<String> ids = jedis.lrange(BOOKS_ALL_KEY, 0, -1);
                if (!ids.isEmpty()) {
                    String[] keys = ids.stream()
                            .map(id -> "book:" + id)
                            .toArray(String[]::new);

                    List<String> jsons = jedis.mget(keys);

                    for (String json : jsons) {
                        if (json != null) {
                            Book book = gson.fromJson(json, Book.class);

                            boolean matchName = (name == null || name.trim().isEmpty())
                                    || (book.getTitle() != null && book.getTitle().toLowerCase().contains(name.trim().toLowerCase()));

                            boolean matchContent = (content == null || content.trim().isEmpty())
                                    || (book.getContent() != null && book.getContent().toLowerCase().contains(content.trim().toLowerCase()));

                            if (matchName) {
                                result.add(book);
                            }
                        }
                    }
                }
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), result.size());

        List<Book> pageContent = (start >= result.size())
                ? new ArrayList<>()
                : result.subList(start, end);

        return new PageImpl<>(pageContent, pageable, result.size());
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        try (Jedis jedis = RedisUtil.getConnection()) {
            for (String idStr : ids) {
                String key = "book:" + idStr;
                String json = jedis.get(key);

                if (json != null) {
                    Book book = gson.fromJson(json, Book.class);

                    removeIndexes(jedis, book);
                    jedis.lrem(BOOKS_ALL_KEY, 0, idStr);
                    jedis.del(key);
                }
            }
        }
    }

    @Override
    public void saveAll(List<Book> books) {
        if (books == null || books.isEmpty()) return;

        try (Jedis jedis = RedisUtil.getConnection()) {
            Pipeline pipeline = jedis.pipelined();

            for (Book book : books) {
                if (book == null) continue;

                if (book.getId() == null) {
                    book.setId(generateId());
                }

                String redisId = String.valueOf(book.getId());
                String key = bookKey(book.getId());

                pipeline.set(key, gson.toJson(book));
                pipeline.rpush(BOOKS_ALL_KEY, redisId);
            }

            pipeline.sync();

            for (Book book : books) {
                if (book != null) {
                    addIndexes(jedis, book);
                }
            }
        }
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        Map<String, Object> result = new HashMap<>();

        try (Jedis jedis = RedisUtil.getConnection()) {
            Map<String, String> hashData = jedis.hgetAll(authorStatsKey(author));

            if (hashData.isEmpty()) {
                result.put("message", "Không tìm thấy dữ liệu tác giả");
                return result;
            }

            Map<String, Long> categoryStats = new HashMap<>();

            for (Map.Entry<String, String> entry : hashData.entrySet()) {
                String key = entry.getKey();
                Long value = Long.parseLong(entry.getValue());

                if ("total".equals(key)) {
                    result.put("tongSoSach", value);
                } else if (key.startsWith("category:")) {
                    String catName = key.replace("category:", "");
                    categoryStats.put(catName, value);
                }
            }

            result.put("thongKeTheoTheLoai", categoryStats);
        }

        return result;
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        List<Book> books = new ArrayList<>();

        try (Jedis jedis = RedisUtil.getConnection()) {
            int start = (int) pageable.getOffset();
            int end = start + pageable.getPageSize() - 1;

            List<String> ids = jedis.lrange(BOOKS_ALL_KEY, start, end);

            if (!ids.isEmpty()) {
                List<String> keys = ids.stream()
                        .map(id -> "book:" + id)
                        .toList();

                List<String> jsons = jedis.mget(keys.toArray(new String[0]));

                for (String json : jsons) {
                    if (json != null) {
                        books.add(gson.fromJson(json, Book.class));
                    }
                }
            }

            long total = jedis.llen(BOOKS_ALL_KEY);
            return new PageImpl<>(books, pageable, total);
        }
    }
}