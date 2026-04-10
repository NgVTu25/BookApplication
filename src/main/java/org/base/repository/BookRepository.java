package org.base.repository;

import org.base.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface BookRepository {

    default void generateAndInsertOneMillionBooks() {
        int batchSize = 500;
        List<Book> batch = new ArrayList<>(batchSize);
        String[] categories = {"Hành động", "Tình cảm", "Khoa học", "Lịch sử", "Kinh dị"};
        String[] authors = {"Nguyen Van A", "Tran Thi B", "Le Van C", "Tolkien", "J.K. Rowling"};

        System.out.println("Bắt đầu sinh 1.000.000 dữ liệu...");
        long start = System.currentTimeMillis();
        Instant now = Instant.now();

        for (int i = 1; i <= 1_000_000; i++) {

            Book book = Book.builder()
                    .author(authors[i % authors.length])
                    .category(categories[i % categories.length])
                    .title("Tiêu đề " + i)
                    .content("Nội dung hấp dẫn " + i)
                    .createDate(now.plusMillis(i * 100L))
                    .viewCount(0L)
                    .downloadCount(0L)
                    .build();

            batch.add(book);

            if (i % batchSize == 0) {
                this.saveAll(batch);
                batch.clear();
                System.out.println("Đã xử lý: " + i + " cuốn...");
            }
        }

        if (!batch.isEmpty()) {
            this.saveAll(batch);
            batch.clear();
        }

        System.out.println("Hoàn tất! Tổng thời gian: " + (System.currentTimeMillis() - start) + " ms");
    }

    void saveAll(List<Book> books);

    Map<String, Object> statisticByAuthor(String author);

    void save(Book book);

    Page<Book> search(String name, String author, String content, Pageable pageable);

    void deleteByIds(List<String> ids);

    Page<Book> findAllPaging(Pageable pageable);

    void update(Long id, Book book);
}
