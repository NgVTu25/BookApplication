package org.base;

import org.base.model.Book;
import org.base.repository.BookFactory;
import org.base.repository.BookRepository;
import org.base.util.INFLUXUtil;
import org.base.util.JPAUtil;
import org.base.util.MongoUtil;
import org.base.util.RedisUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Scanner;

public class BookConsoleApp implements CommandLineRunner {

    private final Scanner scanner = new Scanner(System.in);

    private final String influxUrl = "http://localhost:8086/";
    private final String bucket = "java";
    private final String token = "CBap0zc0WPeZmTgNzNBF5s8mdj1cHzbmw8_0cIEumo-MrLRsUblN1XwRmRDUx8FjfSm0MjDe_LsqK71aX9kREQ==";
    private final String org = "java";
    private final String sqlUrl = "jdbc:mysql://localhost:3306/book_db";
    private final String sqlUsername = "root";
    private final String sqlPassword = "tubeo1012";

    @Override
    public void run(String... args) {
        boolean running = true;

        System.out.println("=== BẮT ĐẦU KHỞI TẠO KẾT NỐI ===");

        try {
            JPAUtil.init(sqlUrl, sqlUsername, sqlPassword);
            System.out.println("[OK] Đã kết nối SQL.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT MYSQL]: " + e.getMessage());
        }

        try {
            RedisUtil.init("localhost", 6379);
            System.out.println("[OK] Đã kết nối Redis.");
        } catch (Exception e) {
            System.err.println("[LỖI] Không thể kết nối Redis. Chắc chắn bạn đã bật Redis Server chưa?");
        }

        try {
            MongoUtil.init("mongodb://localhost:27017", "book");
            System.out.println("[OK] Đã kết nối MongoDB.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT MONGODB]: " + e.getMessage());
        }

        try {
            INFLUXUtil.init(influxUrl, token, org, bucket);
            System.out.println("[OK] Đã kết nối InfluxDB.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT INFLUXDB]: " + e.getMessage());
        }

        while (running) {
            printMainMenu();
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("-> [LỖI] Vui lòng chỉ nhập các số từ 0 đến 6!");
                continue;
            }

            if (choice == 0) {
                running = false;
                System.out.println("Đang thoát chương trình...");
                System.exit(0);
                continue;
            }

            if (choice >= 1 && choice <= 6) {
                System.out.println("Nhập loại DB (1: sql, 2: mongodb, 3: redis, 4: influx): ");
                int dbType;
                try {
                    // SỬA LỖI TẠI ĐÂY: Dùng nextLine rồi ép kiểu để không bị kẹt bộ đệm
                    dbType = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("-> [LỖI] Loại DB không hợp lệ, vui lòng nhập số!");
                    continue;
                }

                try {
                    handleChoice(choice, dbType);
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("-> [LỖI] Lựa chọn không hợp lệ, vui lòng chọn lại!");
            }
        }
    }

    private void printMainMenu() {
        System.out.println("\n========= MENU QUẢN LÝ SÁCH =========");
        System.out.println("1. Lưu sách mới");
        System.out.println("2. Tìm kiếm sách");
        System.out.println("3. Cập nhật sách");
        System.out.println("4. Xóa sách (theo danh sách ID)");
        System.out.println("5. Thống kê theo tác giả");
        System.out.println("6. Xem tất cả (Phân trang)");
        System.out.println("0. Thoát");
        System.out.print("Lựa chọn của bạn: ");
    }

    private void handleChoice(int choice, int dbType) {

        BookRepository database = BookFactory.getRepository(dbType);

        if (database == null) {
            System.out.println("-> [LỖI] Không tìm thấy DB tương ứng với lựa chọn: " + dbType);
            return;
        }

        switch (choice) {
            case 1 -> {
                Book book = new Book();
                System.out.print("Nhập tác giả: "); book.setAuthor(scanner.nextLine());
                System.out.print("Nhập thể loại: "); book.setCategory(scanner.nextLine());
                System.out.print("Nhập tên sách: "); book.setTitle(scanner.nextLine());
                System.out.print("Nhập nội dung: "); book.setContent(scanner.nextLine());

                try {
                    System.out.print("Nhập số lượt xem: "); book.setViewCount(Long.parseLong(scanner.nextLine()));
                    System.out.print("Nhập số lượt tải: "); book.setDownloadCount(Long.parseLong(scanner.nextLine()));
                } catch (NumberFormatException e) {
                    System.out.println("-> [LỖI] Lượt xem/tải phải là số!");
                    return;
                }

                database.save(book);
                System.out.println("Lưu thành công!");
            }
            case 2 -> {
                System.out.print("Từ khóa tên: "); String title = scanner.nextLine();
                System.out.print("Từ khóa tác giả: "); String author = scanner.nextLine();
                System.out.print("Từ khóa nội dung: "); String content = scanner.nextLine();

                var result = database.search(title, author, content, Pageable.ofSize(100));
                if (result == null || result.isEmpty()) {
                    System.out.println("Không tìm thấy kết quả nào.");
                } else {
                    System.out.println("-> KẾT QUẢ TÌM KIẾM:");
                    result.forEach(System.out::println);
                }
            }
            case 3 -> {
                System.out.println("Nhập id sách cần sửa:");
                Long id;
                try {
                    id = Long.parseLong(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("-> [LỖI] ID phải là số hợp lệ!");
                    return;
                }

                Book book = new Book();
                System.out.print("Nhập tác giả mới: "); book.setAuthor(scanner.nextLine());
                System.out.print("Nhập thể loại mới: "); book.setCategory(scanner.nextLine());
                System.out.print("Nhập tên sách mới: "); book.setTitle(scanner.nextLine());
                System.out.print("Nhập nội dung mới: "); book.setContent(scanner.nextLine());

                database.update(id, book);
                System.out.println("Cập nhật thành công!");
            }
            case 4 -> {
                System.out.print("Nhập các ID cách nhau bởi dấu phẩy: ");
                String ids = scanner.nextLine();
                database.deleteByIds(Arrays.asList(ids.split(",")));
                System.out.println("Đã gửi yêu cầu xóa.");
            }
            case 5 -> {
                System.out.print("Nhập tên tác giả: ");
                String author = scanner.nextLine();
                var stats = database.statisticByAuthor(author);
                System.out.println("Kết quả thống kê: " + stats);
            }
            case 6 -> {
                var books = database.findAllPaging(Pageable.ofSize(100));
                if (books == null || books.isEmpty()) {
                    System.out.println("Chưa có sách nào trong database này.");
                } else {
                    books.forEach(System.out::println);
                }
            }
        }
    }
}