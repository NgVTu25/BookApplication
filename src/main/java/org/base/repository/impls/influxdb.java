package org.base.repository.impls;

import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.base.model.Book;
import com.influxdb.client.InfluxDBClient;
import org.base.repository.BookRepository;
import org.base.util.INFLUXUtil;


import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class influxdb implements BookRepository {
    private InfluxDBClient influxDBClient;

    @Override
    public void saveAll(List<Book> books) {
        try (WriteApi writeApi = INFLUXUtil.getClient().makeWriteApi()) {
            for (Book b : books) {
                Point point = Point.measurement("books_metrics")
                        .addTag("author", b.getAuthor())
                        .addTag("category", b.getCategory())
                        .addField("viewCount", b.getViewCount() != null ? b.getViewCount() : 0L)
                        .time(Instant.now(), WritePrecision.NS);

                writeApi.writePoint(point);
            }
        }
        System.out.println("Đã đẩy 1 lô lên InfluxDB");
    }

    @Override
    public void save(Book book) {
        try {
            Point point = Point.measurement("books_metrics")
                    .addTag("author", book.getAuthor())
                    .addTag("category", book.getCategory())
                    .addField("viewCount", book.getViewCount() != null ? book.getViewCount() : 0L)
                    .time(Instant.now(), WritePrecision.NS);

            INFLUXUtil.getWriteApi().writePoint(point);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Book> search(String title, String author, String content) {
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
        return books;
    }

    @Override
    public void deleteByIds(List<String> ids) {
        OffsetDateTime start = OffsetDateTime.now().minusDays(7);
        OffsetDateTime stop = OffsetDateTime.now();

        String predicate = "author = \"Tolkien\"";

        INFLUXUtil.getClient().getDeleteApi().delete(start, stop, predicate,
                INFLUXUtil.getBucket(), INFLUXUtil.getOrg());
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
                        "|> range(start: -24h) " +
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
}
