package org.base.repository.impls;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

import org.base.config.ConfigFactory;
import org.base.model.Book;
import org.base.repository.BookRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class InfluxdbBookRepository implements BookRepository {

    private final InfluxDBClient influxDBClient;

    private final String org;
    private final String bucket;

    private static final String MEASUREMENT = "Book";


    public InfluxdbBookRepository() {
        ConfigFactory configFactory = ConfigFactory.getInstance();

        String url = configFactory.getConfig("influxdb.url");
        String token = configFactory.getConfig("influxdb.token");
        this.org = configFactory.getConfig("influxdb.org");
        this.bucket = configFactory.getConfig("influxdb.bucket");

        if (url == null || url.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình influxdb.url");
        }
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình influxdb.token");
        }
        if (org == null || org.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình influxdb.org");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình influxdb.bucket");
        }

        this.influxDBClient = InfluxDBClientFactory.create(
                url,
                token.toCharArray(),
                org,
                bucket
        );

        System.out.println("[OK] InfluxDBClient initialized");
    }

    private List<Book> mapFluxToBooks(String fluxQuery) {
        List<Book> books = new ArrayList<>();
        List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Book book = new Book();

                Object idObj = record.getValueByKey("id");
                if (idObj != null) {
                    try {
                        book.setId(Long.parseLong(idObj.toString()));
                    } catch (NumberFormatException ignore) {
                    }
                }

                Object title = record.getValueByKey("title");
                if (title != null) book.setTitle(title.toString());

                Object author = record.getValueByKey("author");
                if (author != null) book.setAuthor(author.toString());

                Object category = record.getValueByKey("category");
                if (category != null) book.setCategory(category.toString());

                Object content = record.getValueByKey("content");
                if (content != null) book.setContent(content.toString());

                Object viewCount = record.getValueByKey("viewCount");
                if (viewCount != null) {
                    try {
                        book.setViewCount(Long.parseLong(viewCount.toString()));
                    } catch (NumberFormatException ignore) {
                    }
                }

                Object downloadCount = record.getValueByKey("downloadCount");
                if (downloadCount != null) {
                    try {
                        book.setDownloadCount(Long.parseLong(downloadCount.toString()));
                    } catch (NumberFormatException ignore) {
                    }
                }

                books.add(book);
            }
        }
        return books;
    }

    @Override
    public void save(Book book) {
        if (book.getId() == null) {
            book.setId(System.currentTimeMillis() + new Random().nextInt(1000));
        }

        Point point = Point.measurement(MEASUREMENT)
                .addTag("author", book.getAuthor() == null ? "Unknown" : book.getAuthor())
                .addTag("category", book.getCategory() == null ? "General" : book.getCategory())
                .addField("id", book.getId())
                .addField("title", book.getTitle() == null ? "" : book.getTitle())
                .addField("content", book.getContent() != null)
                .addField("viewCount", book.getViewCount() == null ? 0L : book.viewCount)
                .addField("downloadCount", book.getDownloadCount() == null ? 0L : book.downloadCount)
                .time(Instant.now(), WritePrecision.NS);

        influxDBClient.getWriteApiBlocking().writePoint(bucket, org, point);
    }

    @Override
    public void saveAll(List<Book> books) {
        if (books == null || books.isEmpty()) return;

        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            for (Book book : books) {
                if (book.getId() == null) book.setId(System.currentTimeMillis());

                Point point = Point.measurement(MEASUREMENT)
                        .addTag("author", book.getAuthor())
                        .addTag("category", book.getCategory())
                        .addField("id", book.getId())
                        .addField("title", book.getTitle())
                        .addField("viewCount", 0L)
                        .addField("downloadCount", 0L)
                        .time(Instant.now(), WritePrecision.NS);
                writeApi.writePoint(point);
            }
            writeApi.flush();
        }
        System.out.println("Batch save " + books.size() + " books successful.");
    }

    @Override
    public Page<Book> search(String title, String author, String content, Pageable pageable) {
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 10 : pageable.getPageSize();
        int offset = page * size;

        StringBuilder flux = new StringBuilder();
        flux.append(String.format("from(bucket: \"%s\") ", bucket))
                .append("|> range(start: 0) ")
                .append(String.format("|> filter(fn: (r) => r._measurement == \"%s\") ", MEASUREMENT))
                .append("|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") ");

        if (title != null && !title.isBlank()) {
            flux.append(String.format("|> filter(fn: (r) => r.title =~ /(?i)%s/) ", title));
        }
        if (author != null && !author.isBlank()) {
            flux.append(String.format("|> filter(fn: (r) => r.author =~ /(?i)%s/) ", author));
        }

        flux.append(String.format("|> limit(n: %d, offset: %d)", size, offset));

        List<Book> books = mapFluxToBooks(flux.toString());

        return new PageImpl<>(books, PageRequest.of(page, size), books.size());
    }

    @Override
    public void update(Long id, Book book) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: 0) " +
                        "|> filter(fn: (r) => r._measurement == \"%s\") " +
                        "|> filter(fn: (r) => r.id == \"%s\") " +
                        "|> limit(n: 1)",
                bucket, MEASUREMENT, id.toString()
        );

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        boolean exists = tables.stream().anyMatch(t -> !t.getRecords().isEmpty());
        if (!exists) {
            System.out.println("Update không thành công: Không tìm thấy ID");
            return;
        }

        deleteByIds(List.of(id.toString()));
        book.setId(id);
        save(book);
        System.out.println("Cập nhật thành công bản ghi InfluxDB");
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        OffsetDateTime start = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime stop = OffsetDateTime.now();

        String predicate = ids.stream()
                .map(id -> "id = \"" + id + "\"")
                .collect(Collectors.joining(" OR "));

        String finalPredicate = "(_measurement = \"" + MEASUREMENT + "\") AND (" + predicate + ")";

        try {
            influxDBClient.getDeleteApi().delete(start, stop, finalPredicate, bucket, org);
            System.out.println("Đã xóa các sách có ID: " + ids);
        } catch (Exception e) {
            System.err.println("Lỗi khi xóa dữ liệu InfluxDB: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: 0) " +
                        "|> filter(fn: (r) => r._measurement == \"%s\") " +
                        "|> filter(fn: (r) => r.id != \"\") " +
                        "|> filter(fn: (r) => r.author == \"%s\") " +
                        "|> group(columns: [\"id\"]) " +
                        "|> first()",
                bucket, MEASUREMENT, author
        );

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        long totalBooks = tables.stream()
                .mapToLong(t -> t.getRecords().size())
                .sum();

        if (totalBooks == 0) {
            return Map.of("message", "Không tìm thấy dữ liệu tác giả: " + author);
        }

        return Map.of(
                "author", author,
                "totalBooks", totalBooks,
                "lastUpdated", Instant.now()
        );
    }

    public long countTotalBooks() {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r._measurement == \"%s\") " +
                        "|> filter(fn: (r) => r._field == \"id\") " +
                        "|> last() |> group() |> count()",
                bucket, MEASUREMENT
        );
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        if (!tables.isEmpty() && !tables.getFirst().getRecords().isEmpty()) {
            return Long.parseLong(Objects.requireNonNull(tables.getFirst().getRecords().getFirst().getValueByKey("_value")).toString());
        }
        return 0L;
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        int size = pageable.getPageSize();
        int offset = (int) pageable.getOffset();

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r._measurement == \"%s\") " +
                        "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                        "|> sort(columns: [\"_time\"], desc: true) " +
                        "|> limit(n: %d, offset: %d)",
                bucket, MEASUREMENT, size, offset
        );

        List<Book> books = mapFluxToBooks(flux);
        long total = countTotalBooks();

        return new PageImpl<>(books, pageable, total);
    }
}