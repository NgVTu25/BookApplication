package org.base.repository;


import org.base.repository.impls.influxdb;
import org.base.repository.impls.mongodb;
import org.base.repository.impls.redis;
import org.base.repository.impls.sql;

public class BookFactory {


    public static BookRepository getRepository(String databaseType) {
        if (databaseType == null) {
            throw new IllegalArgumentException("Loại Database không được để trống!");
        }

        switch (databaseType.toLowerCase()) {
            case "sql":
                return new sql();

            case "redis":
                return new redis();

            case "influx":
                return new influxdb();

            case "mongodb":
                 return new mongodb();

            default:
                throw new IllegalArgumentException("Hệ thống chưa hỗ trợ loại Database: " + databaseType);
        }
    }
}