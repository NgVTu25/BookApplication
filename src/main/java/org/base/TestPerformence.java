package org.base;

import org.base.repository.BookRepository;
import org.base.repository.impls.MongodbBookRepository;
import org.base.repository.impls.RedisBookRepository;
import org.base.repository.impls.SqlBookRepository;
import org.base.util.INFLUXUtil;
import org.base.util.JPAUtil;
import org.base.util.MongoUtil;
import org.base.util.RedisUtil;

import java.util.Map;

public class TestPerformence {

    private static final String influxUrl = "http://localhost:8086/";

    private static final String bucket = "java";

    private static final String token = "CBap0zc0WPeZmTgNzNBF5s8mdj1cHzbmw8_0cIEumo-MrLRsUblN1XwRmRDUx8FjfSm0MjDe_LsqK71aX9kREQ==";

    private static final String org = "java";

    private static final String sqlUrl = "jdbc:mysql://localhost:3306/book_db";

    private static final String sqlUsername = "root";

    private static final String sqlPassword = "tubeo1012";

    public static void main(String[] args) {
        System.out.println("=== BẮT ĐẦU KHỞI TẠO KẾT NỐI ===");

        try {
            JPAUtil.init(sqlUrl, sqlUsername, sqlPassword);
            System.out.println("[OK] Đã kết nối SQL.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT]:");
            e.printStackTrace();
        }

        try {
            RedisUtil.init("localhost", 6379, 0);
            System.out.println("[OK] Đã kết nối Redis.");
        } catch (Exception e) {
            System.err.println("[LỖI] Không thể kết nối Redis.");
        }

        try {
            MongoUtil.init("mongodb://localhost:27017", "book");
            System.out.println("[OK] Đã kết nối SQL.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT]:");
            e.printStackTrace();
        }

        try {
            INFLUXUtil.init(influxUrl, token, org, bucket);
            System.out.println("[OK] Đã kết nối SQL.");
        } catch (Exception e) {
            System.err.println("[LỖI CHI TIẾT]:");
            e.printStackTrace();
        }

        try {
            System.out.println("\n================================");
            System.out.println("        TEST SQL (JPA)           ");
            System.out.println("=================================");
            BookRepository sqlRepo = new SqlBookRepository();

            System.out.println("Đang tạo và lưu 1.000.000 bản ghi ");
            long startSql = System.currentTimeMillis();
            sqlRepo.generateAndInsertOneMillionBooks();
            long endSql = System.currentTimeMillis();
            System.out.println("-> Thời gian INSERT SQL: " + (endSql - startSql) + " ms");

            System.out.println("\nĐang thống kê sách của tác giả 'Tolkien' trên SQL...");
            long startStatSql = System.currentTimeMillis();
            Map<String, Object> sqlStats = sqlRepo.statisticByAuthor("Tolkien");
            long endStatSql = System.currentTimeMillis();
            System.out.println(sqlStats);
            System.out.println("-> Thời gian THỐNG KÊ SQL: " + (endStatSql - startStatSql) + " ms");

            System.out.println("\n================================");
            System.out.println("          TEST REDIS             ");
            System.out.println("=================================");
            BookRepository redisRepo = new RedisBookRepository();

            System.out.println("Đang tạo và lưu 1.000.000 bản ghi vào Redis");
            long startRedis = System.currentTimeMillis();
            redisRepo.generateAndInsertOneMillionBooks();
            long endRedis = System.currentTimeMillis();
            System.out.println("-> Thời gian INSERT Redis: " + (endRedis - startRedis) + " ms");

            System.out.println("\nĐang lấy thống kê của tác giả 'Tolkien' trên Redis...");
            long startStatRedis = System.currentTimeMillis();
            Map<String, Object> redisStats = redisRepo.statisticByAuthor("Tolkien");
            long endStatRedis = System.currentTimeMillis();
            System.out.println(redisStats);
            System.out.println("-> Thời gian THỐNG KÊ Redis: " + (endStatRedis - startStatRedis) + " ms");

            System.out.println("\n================================");
            System.out.println("          TEST MONGODB            ");
            System.out.println("=================================");
            BookRepository mongodbRepo = new MongodbBookRepository();
            mongodbRepo.generateAndInsertOneMillionBooks();


            long startStatMongodb = System.currentTimeMillis();
            Map<String, Object> MongodbStats = mongodbRepo.statisticByAuthor("Tolkien");
            long endStatMongodb = System.currentTimeMillis();
            System.out.println(MongodbStats);
            System.out.println("-> Thời gian THỐNG KÊ Mongodb: " + (endStatMongodb - startStatMongodb) + " ms");

//            System.out.println("\n===============================");
//            System.out.println("          TEST INFLUX            ");
//            System.out.println("=================================");
//            BookRepository influx = new InfluxdbBookRepository();
//            influx.generateAndInsertOneMillionBooks();
//
//            long startStatInflux = System.currentTimeMillis();
//            Map<String, Object> InfluxStats = influx.statisticByAuthor("Tolkien");
//            long endStatInfluxd = System.currentTimeMillis();
//            System.out.println(InfluxStats);
//            System.out.println("-> Thời gian THỐNG KÊ influx: " + (endStatInfluxd - startStatInflux) + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("\n=== ĐÓNG KẾT NỐI ===");
            JPAUtil.close();
            RedisUtil.close();
            System.out.println("Chương trình kết thúc!");
        }
    }
}
