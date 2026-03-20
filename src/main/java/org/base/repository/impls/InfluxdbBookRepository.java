package org.base.repository.impls;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.base.util.INFLUXUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class InfluxdbBookRepository implements BookRepository {

    private static final String MEASUREMENT = "books_metrics";

    private final InfluxDBClient influxDBClient = INFLUXUtil.getClient();

    public InfluxdbBookRepository() {
    }

    @Override
    public void saveAll(List<Book> books) {
        if (books == null || books.isEmpty()) {
            return;
        }

        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            List<Point> points = new ArrayList<>();
            long startNano = System.currentTimeMillis() * 1_000_000L;

            for (int i = 0; i < books.size(); i++) {
                Book book = books.get(i);

                Long id = book.getId() != null
                        ? book.getId()
                        : ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);

                book.setId(id);

                Point point = Point.measurement(MEASUREMENT)
                        .addTag("id", String.valueOf(id))
                        .addTag("author", defaultString(book.getAuthor(), "Unknown"))
                        .addTag("category", defaultString(book.getCategory(), "Unknown"))
                        .addField("title", defaultString(book.getTitle(), ""))
                        .addField("content", defaultString(book.getContent(), ""))
                        .addField("viewCount", book.getViewCount() != null ? book.getViewCount() : 0L)
                        .addField("downloadCount", book.getDownloadCount() != null ? book.getDownloadCount() : 0L)
                        .time(startNano + i, WritePrecision.NS);

                points.add(point);
            }

            writeApi.writePoints(points);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save books to InfluxDB", e);
        }

        System.out.println("Đã đẩy " + books.size() + " bản ghi lên InfluxDB");
    }

    @Override
    public void save(Book book) {
        if (book == null) {
            return;
        }

        try {
            Long id = book.getId() != null
                    ? book.getId()
                    : ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);

            book.setId(id);

            Point point = Point.measurement(MEASUREMENT)
                    .addTag("id", String.valueOf(id))
                    .addTag("author", defaultString(book.getAuthor(), "Unknown"))
                    .addTag("category", defaultString(book.getCategory(), "Unknown"))
                    .addField("title", defaultString(book.getTitle(), ""))
                    .addField("content", defaultString(book.getContent(), ""))
                    .addField("viewCount", book.getViewCount() != null ? book.getViewCount() : 0L)
                    .addField("downloadCount", book.getDownloadCount() != null ? book.getDownloadCount() : 0L)
                    .time(Instant.now(), WritePrecision.NS);

            try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
                writeApi.writePoint(point);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save book to InfluxDB", e);
        }
    }

    @Override
    public Page<Book> search(String title, String author, String content, Pageable pageable) {
        String bucket = INFLUXUtil.getBucket();

        StringBuilder flux = new StringBuilder();
        flux.append(String.format("from(bucket: \"%s\") ", bucket))
                .append("|> range(start: 0) ")
                .append(String.format("|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") ", MEASUREMENT))
                .append("|> pivot(rowKey:[\"_time\"], columnKey:[\"_field\"], valueColumn:\"_value\") ")
                .append("|> group() ")
                .append("|> sort(columns:[\"_time\"], desc:true) ");

        if (author != null && !author.trim().isEmpty()) {
            String safeAuthor = escapeRegex(author.trim());
            flux.append(String.format("|> filter(fn: (r) => r[\"author\"] =~ /(?i).*%s.*/) ", safeAuthor));
        }

        if (title != null && !title.trim().isEmpty()) {
            String safeTitle = escapeRegex(title.trim());
            flux.append(String.format("|> filter(fn: (r) => r[\"title\"] =~ /(?i).*%s.*/) ", safeTitle));
        }

        if (content != null && !content.trim().isEmpty()) {
            String safeContent = escapeRegex(content.trim());
            flux.append(String.format("|> filter(fn: (r) => r[\"content\"] =~ /(?i).*%s.*/) ", safeContent));
        }

        List<Book> books = deduplicateLatestBooks(mapFluxToBooks(flux.toString()));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), books.size());
        List<Book> pageContent = start >= books.size() ? Collections.emptyList() : books.subList(start, end);

        return new PageImpl<>(pageContent, pageable, books.size());
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        OffsetDateTime start = OffsetDateTime.now().minusYears(50);
        OffsetDateTime stop = OffsetDateTime.now().plusDays(1);

        try {
            for (String id : ids) {
                if (id == null || id.trim().isEmpty()) {
                    continue;
                }

                String cleanId = id.trim();
                String predicate = String.format("_measurement=\"%s\" AND id=\"%s\"", MEASUREMENT, cleanId);

                influxDBClient.getDeleteApi().delete(
                        start,
                        stop,
                        predicate,
                        INFLUXUtil.getBucket(),
                        INFLUXUtil.getOrg()
                );
            }

            System.out.println("Đã gửi yêu cầu xóa các id: " + ids + " trên InfluxDB.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete books in InfluxDB", e);
        }
    }

    @Override
    public void update(Long id, Book book) {
        if (id == null || book == null) {
            return;
        }

        deleteByIds(List.of(String.valueOf(id)));
        book.setId(id);
        save(book);
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        Map<String, Object> result = new HashMap<>();
        result.put("author", author);

        if (author == null || author.trim().isEmpty()) {
            result.put("total_records", 0L);
            return result;
        }

        String safeAuthor = author.trim();

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: 0) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"author\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"title\") ",
                INFLUXUtil.getBucket(), MEASUREMENT, safeAuthor
        );

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, INFLUXUtil.getOrg());

        long total = 0L;
        for (FluxTable table : tables) {
            total += table.getRecords().size();
        }

        result.put("total_records", total);
        return result;
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        String bucket = INFLUXUtil.getBucket();

        String flux = String.format("from(bucket: \"%s\") ", bucket) +
                "|> range(start: 0) " +
                String.format("|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") ", MEASUREMENT) +
                "|> pivot(rowKey:[\"_time\"], columnKey:[\"_field\"], valueColumn:\"_value\") " +
                "|> group() " +
                "|> sort(columns:[\"_time\"], desc:true) ";

        List<Book> books = deduplicateLatestBooks(mapFluxToBooks(flux));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), books.size());
        List<Book> pageContent = start >= books.size() ? Collections.emptyList() : books.subList(start, end);

        return new PageImpl<>(pageContent, pageable, books.size());
    }

    private List<Book> mapFluxToBooks(String fluxQuery) {
        List<Book> books = new ArrayList<>();
        List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, INFLUXUtil.getOrg());

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Book book = new Book();

                Object idObj = record.getValueByKey("id");
                if (idObj != null) {
                    try {
                        book.setId(Long.parseLong(idObj.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                Object title = record.getValueByKey("title");
                if (title != null) {
                    book.setTitle(title.toString());
                }

                Object author = record.getValueByKey("author");
                if (author != null) {
                    book.setAuthor(author.toString());
                }

                Object category = record.getValueByKey("category");
                if (category != null) {
                    book.setCategory(category.toString());
                }

                Object content = record.getValueByKey("content");
                if (content != null) {
                    book.setContent(content.toString());
                }

                Object viewCount = record.getValueByKey("viewCount");
                if (viewCount != null) {
                    try {
                        book.setViewCount(Long.parseLong(viewCount.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                Object downloadCount = record.getValueByKey("downloadCount");
                if (downloadCount != null) {
                    try {
                        book.setDownloadCount(Long.parseLong(downloadCount.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                books.add(book);
            }
        }

        return books;
    }

    private List<Book> deduplicateLatestBooks(List<Book> books) {
        Map<Long, Book> latestBooks = new LinkedHashMap<>();

        for (Book book : books) {
            if (book.getId() != null) {
                latestBooks.putIfAbsent(book.getId(), book);
            }
        }

        return new ArrayList<>(latestBooks.values());
    }

    private String defaultString(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String escapeRegex(String input) {
        return Pattern.quote(input);
    }
}