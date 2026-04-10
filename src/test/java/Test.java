import org.base.model.Book;
import org.base.repository.BookFactory;
import org.base.repository.BookRepository;
import org.base.util.INFLUXUtil;
import org.base.util.JPAUtil;
import org.base.util.MongoUtil;
import org.base.util.RedisUtil;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class Test {
    public static void main(String[] args) {
        System.out.println("========== KHỞI TẠO KẾT NỐI HỆ THỐNG ==========");
        try {
            JPAUtil.init("jdbc:mysql://localhost:3306/book_db", "root", "tubeo1012");
            System.out.println("[OK] Đã kết nối MySQL.");

            RedisUtil.init("localhost", 6379, 0);
            System.out.println("[OK] Đã kết nối Redis.");

            MongoUtil.init("mongodb://localhost:27017", "book");
            System.out.println("[OK] Đã kết nối MongoDB.");

            INFLUXUtil.init("http://localhost:8086/",
                    "CBap0zc0WPeZmTgNzNBF5s8mdj1cHzbmw8_0cIEumo-MrLRsUblN1XwRmRDUx8FjfSm0MjDe_LsqK71aX9kREQ==",
                    "java", "java");
            System.out.println("[OK] Đã kết nối InfluxDB.");

            Book testBook = Book.builder()
                    .title("Sách Test Tổng Hợp")
                    .author("Tác Giả Kiểm Thử")
                    .category("Công Nghệ")
                    .content("Nội dung dùng để kiểm tra tất cả các database...")
                    .viewCount(999L)
                    .downloadCount(100L)
                    .createDate(Instant.now())
                    .build();

            int[] dbTypes = {1, 2, 3, 4};
            String[] dbNames = {"MYSQL (JPA)", "MONGODB", "REDIS", "INFLUXDB"};

            for (int i = 0; i < dbTypes.length; i++) {
                int type = dbTypes[i];
                String name = dbNames[i];

                System.out.println("\n" + "=".repeat(20) + " ĐANG KIỂM TRA: " + name + " " + "=".repeat(20));
                BookRepository repository = BookFactory.getRepository(type);

                repository.save(testBook);
                System.out.println("1. [LƯU] Thành công.");

                var searchPage = repository.search("Sách Test", "", "", PageRequest.of(0, 1));
                List<Book> searchResults = searchPage.getContent();

                if (searchResults.isEmpty()) {
                    System.out.println("   [!] Không tìm thấy sách để thực hiện các bước tiếp theo.");
                    continue;
                }

                Book foundBook = searchResults.getFirst();
                Long currentId = foundBook.getId();
                System.out.println("2. [TÌM KIẾM] Tìm thấy sách với ID: " + currentId);

                Book updateInfo = Book.builder()
                        .title("Sách Đã Cập Nhật")
                        .content("Nội dung mới sau khi update")
                        .build();
                repository.update(currentId, updateInfo);
                System.out.println("3. [CẬP NHẬT] Đã đổi tên thành: 'Sách Đã Cập Nhật'");

                Map<String, Object> stats = repository.statisticByAuthor("Tác Giả Kiểm Thử");
                System.out.println("4. [THỐNG KÊ] Tác giả 'Tác Giả Kiểm Thử': " + stats);

                var allBooks = repository.findAllPaging(PageRequest.of(0, 10));
                System.out.println("5. [XEM TẤT CẢ] Tổng số bản ghi trên trang 1: " + allBooks.getContent().size());

                repository.deleteByIds(List.of(String.valueOf(currentId)));
                System.out.println("6. [XÓA] Đã xóa sách ID: " + currentId);

                var checkDeleted = repository.search("", "", "", PageRequest.of(0, 10));
                boolean stillExists = checkDeleted.getContent().stream()
                        .anyMatch(b -> b.getId().equals(currentId));
                System.out.println("   => Kiểm tra sau xóa: " + (stillExists ? "Thất bại (Vẫn còn)" : "Thành công (Đã mất)"));
            }

            System.out.println("\n===============================================");
            System.out.println(">>> TẤT CẢ CÁC THAO TÁC ĐÃ ĐƯỢC KIỂM TRA XONG <<<");

        } catch (Exception e) {
            System.err.println("!!! LỖI TRONG QUÁ TRÌNH KIỂM THỬ: " + e.getMessage());
            e.printStackTrace();
        } finally {
            JPAUtil.close();
            RedisUtil.close();
            MongoUtil.close();
            INFLUXUtil.close();
            System.out.println("\n>>> Hệ thống đã đóng tất cả kết nối an toàn.");
        }
    }
}