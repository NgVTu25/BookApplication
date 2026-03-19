package org.base.repository;


import org.base.repository.impls.InfluxdbBookRepository;
import org.base.repository.impls.MongodbBookRepository;
import org.base.repository.impls.RedisBookRepository;
import org.base.repository.impls.SqlBookRepository;

public class BookFactory {


    public static BookRepository getRepository(String databaseType) {
        if (databaseType == null) {
            throw new IllegalArgumentException("Loại Database không được để trống!");
        }

        switch (databaseType.toLowerCase()) {
            case "sql":
                return new SqlBookRepository();

            case "redis":
                return new RedisBookRepository();

            case "influx":
                return new InfluxdbBookRepository();

            case "mongodb":
                 return new MongodbBookRepository();

            default:
                throw new IllegalArgumentException("Hệ thống chưa hỗ trợ loại Database: " + databaseType);
        }
    }
}