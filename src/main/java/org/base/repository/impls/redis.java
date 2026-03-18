package org.base.repository.impls;

import com.google.gson.*;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.base.util.RedisUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class redis implements BookRepository {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) -> LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    @Override
    public void save(Book book) {
        book.setContent(null);
        try (Jedis jedis = RedisUtil.getConnection()) {
            String json = gson.toJson(book);
            jedis.set("book:" + book.getId(), json);

            if(book.getAuthor() != null) {
                jedis.sadd("author:" + book.getAuthor() + ":books");
            }
        }
    }

    @Override
    public void update(Long id, Book book) {
        if (id == null) {
            System.out.println("Lỗi: ID không được để trống!");
            return;
        }
        book.setId(id);
        book.setContent(null);

        try (Jedis jedis = RedisUtil.getConnection()) {
            String key = "book:" + id;

            String oldJson = jedis.get(key);

            if (oldJson == null) {
                System.out.println("Không tìm thấy sách với ID: " + id + " để cập nhật!");
                return;
            }

            Book oldBook = gson.fromJson(oldJson, Book.class);
            if (oldBook.getAuthor() != null && !oldBook.getAuthor().equals(book.getAuthor())) {
                jedis.srem("author:" + oldBook.getAuthor() + ":books", String.valueOf(id));

                jedis.hincrBy("stats:author:" + oldBook.getAuthor(), "total", -1);
                jedis.hincrBy("stats:author:" + oldBook.getAuthor(), "category:" + oldBook.getCategory(), -1);
            }
            this.save(book);
            System.out.println("Cập nhật thành công sách ID: " + id);
        }
    }


    @Override
    public List<Book> search(String name, String author, String content) {
        List<Book> result = new ArrayList<>();

        try (Jedis jedis = RedisUtil.getConnection()) {
            if (author != null && !author.isEmpty()) {
                Set<String> bookIds = jedis.smembers("author:" + author + ":books");

                if (bookIds.isEmpty()) return result;

                String[] keys = bookIds.stream().map(id -> "book:" + id).toArray(String[]::new);
                List<String> jsons = jedis.mget(keys);

                for (String json : jsons) {
                    if (json != null) {
                        gson.fromJson(json, Book.class);
                    }
                }
            }
            else {
                System.out.println("Cảnh báo: Tìm kiếm full-text trên Redis thuần rất chậm!");
            }
        }
        return result;
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        try (Jedis jedis = RedisUtil.getConnection()) {
            String[] keys = ids.stream().map(id -> "book:" + id).toArray(String[]::new);

            jedis.del(keys);

        }
    }

    @Override
    public void saveAll(List<Book> books) {
        try (Jedis jedis = RedisUtil.getConnection()) {
            Pipeline p = jedis.pipelined();
            for (Book b : books) {
                String redisId = (b.getId() == null) ? UUID.randomUUID().toString() : b.getId().toString();

                String bookKey = "book:" + redisId;
                String authorKey = "stats:author:" + b.getAuthor();

                p.set(bookKey, gson.toJson(b));

                p.sadd("author:" + b.getAuthor() + ":books", redisId);

                p.hincrBy(authorKey, "total", 1);
                p.hincrBy(authorKey, "category:" + b.getCategory(), 1);
            }
            p.sync();
        }
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        Map<String, Object> result = new HashMap<>();
        try (Jedis jedis = RedisUtil.getConnection()) {
            Map<String, String> hashData = jedis.hgetAll("stats:author:" + author);

            if (hashData.isEmpty()) {
                result.put("Message", "Không tìm thấy dữ liệu tác giả");
                return result;
            }

            Map<String, Long> categoryStats = new HashMap<>();

            for (Map.Entry<String, String> entry : hashData.entrySet()) {
                String key = entry.getKey();
                Long value = Long.parseLong(entry.getValue());

                if (key.equals("total")) {
                    result.put("Tổng số sách", value);
                } else if (key.startsWith("category:")) {
                    String catName = key.replace("category:", "");
                    categoryStats.put(catName, value);
                }
            }
            result.put("Thống kê theo thể loại", categoryStats);
        }
        return result;
    }


}
