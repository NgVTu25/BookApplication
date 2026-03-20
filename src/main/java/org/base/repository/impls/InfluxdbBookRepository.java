package org.base.repository.impls;

import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.base.model.Book;
import com.influxdb.client.InfluxDBClient;
import org.base.repository.BookRepository;
import org.base.util.INFLUXUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;


import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


public class InfluxdbBookRepository implements BookRepository {
    private final InfluxDBClient influxDBClient = INFLUXUtil.getClient();
    private final String influxUrl = "http://localhost:8086/";

    private final String token = "CBap0zc0WPeZmTgNzNBF5s8mdj1cHzbmw8_0cIEumo-MrLRsUblN1XwRmRDUx8FjfSm0MjDe_LsqK71aX9kREQ==";

    private final String org = "java";

    private final String MEASUREMENT = "books_metrics";

    public InfluxdbBookRepository( ) {
    }


    @Override
    public void saveAll(List<Book> books) {
        try (WriteApi writeApi = INFLUXUtil.getClient().makeWriteApi()) {
            List<Point> points = new ArrayList<>();

            long startNano = System.currentTimeMillis() * 1_000_000L;

            for (int i = 0; i < books.size(); i++) {
                Book b = books.get(i);
                if (b.getId() == null) {
                    Long newId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
                    b.setId(newId);
                }

                Point point = Point.measurement(MEASUREMENT)
                        .addTag("author", b.getAuthor())
                        .addTag("category", b.getCategory())
                        .addField("id", b.getId())
                        .addField("title", b.getTitle())
                        .addField("viewCount", b.getViewCount() != null ? b.getViewCount() : 0L)
                        .time(startNano + i, WritePrecision.NS);

                points.add(point);
            }
            writeApi.writePoints(points);
        }
        System.out.println("Đã đẩy " + books.size() + " bản ghi lên InfluxDB");
    }

    @Override
    public void save(Book book) {
        try {
            Long id = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
            Point point = Point.measurement("books_metrics")
                    .addTag("author", book.getAuthor())
                    .addTag("id", id.toString())
                    .addTag("category", book.getCategory())
                    .addField("viewCount", book.getViewCount() != null ? book.getViewCount() : 0L)
                    .time(Instant.now(), WritePrecision.NS);

            INFLUXUtil.getWriteApi().writePoint(point);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Page<Book> search(String title, String author, String content, Pageable pageable) {
        List<Book> books = new ArrayList<>();

        StringBuilder flux = new StringBuilder();
        flux.append(String.format("from(bucket: \"%s\") ", INFLUXUtil.getBucket()))
                .append("|> range(start: -30d) ")
                .append("|> filter(fn: (r) => r[\"_measurement\"] == \"books_metrics\") ");

        if (author != null) {
            flux.append(String.format("|> filter(fn: (r) => r[\"author\"] == \"%s\") ", author));
        }

        if (title != null) {
            flux.append(String.format("|> filter(fn: (r) => r[\"title\"] == \"%s\") ", title));
        }

        List<FluxTable> tables = INFLUXUtil.getClient().getQueryApi().query(flux.toString(), INFLUXUtil.getOrg());

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Book b = new Book();
                b.setAuthor((String) record.getValueByKey("author"));
                books.add(b);
            }
        }
        return new PageImpl<>(books, pageable, books.size());
    }
    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        OffsetDateTime start = OffsetDateTime.now().minusYears(1);
        OffsetDateTime stop = OffsetDateTime.now();

        String predicate = ids.stream()
                .map(id -> "id = \"" + id + "\"")
                .collect(Collectors.joining(" or "));

        String finalPredicate = "_measurement = \"Book\" and (" + predicate + ")";

        try {
            INFLUXUtil.getClient().getDeleteApi().delete(
                    start,
                    stop,
                    finalPredicate,
                    INFLUXUtil.getBucket(),
                    INFLUXUtil.getOrg()
            );
            System.out.println("Deleted ids: " + ids);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Long id, Book book) {
        save(book);
    }

    @Override
    public Map<String, Object> statisticByAuthor(String author) {
        Map<String, Object> result = new HashMap<>();

        String query = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -24d) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"books_metrics\") " +
                        "|> filter(fn: (r) => r[\"author\"] == \"%s\") " +
                        "|> count()",
                INFLUXUtil.getBucket(), author
        );

        List<FluxTable> tables = INFLUXUtil.getClient().getQueryApi().query(query, INFLUXUtil.getOrg());

        long total = 0;
        for (FluxTable table : tables) {
            total += table.getRecords().size();
        }

        result.put("author", author);
        result.put("total_records_recent", total);
        return result;
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        String bucket = "java";
        String flux = String.format("from(bucket: \"%s\") ", bucket) +
                "|> range(start: 0) " +
                String.format("|> filter(fn: (r) => r._measurement == \"%s\") ", MEASUREMENT) +
                "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> group() " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                String.format("|> limit(n: %d, offset: %d)", pageable.getPageSize(), pageable.getOffset());

        String countFlux = String.format("from(bucket: \"%s\") ", bucket) +
                "|> range(start: 0) " +
                String.format("|> filter(fn: (r) => r._measurement == \"%s\") ", MEASUREMENT) +
                "|> count(column: \"_value\") " +
                "|> group()";

        List<Book> books = mapFluxToBooks(flux);
        long total = getTotalCount(countFlux);

        return new PageImpl<>(books, pageable, total);
    }
    private List<Book> mapFluxToBooks(String fluxQuery) {
        List<Book> books = new ArrayList<>();
        List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Book book = new Book();

                Object idObj = record.getValueByKey("id");
                if (idObj != null) {
                    book.setId(Long.parseLong(idObj.toString()));
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
                if (viewCount != null) book.setViewCount(Long.parseLong(viewCount.toString()));

                Object downloadCount = record.getValueByKey("downloadCount");
                if (downloadCount != null) book.setDownloadCount(Long.parseLong(downloadCount.toString()));

                books.add(book);
            }
        }
        return books;
    }

    private long getTotalCount(String countQuery) {
        List<FluxTable> tables = influxDBClient.getQueryApi().query(countQuery);
        if (tables.isEmpty() || tables.getFirst().getRecords().isEmpty()) {
            return 0L;
        }

        Object countObj = tables.getFirst().getRecords().getFirst().getValueByKey("_value");
        return countObj != null ? Long.parseLong(countObj.toString()) : 0L;
    }
}
