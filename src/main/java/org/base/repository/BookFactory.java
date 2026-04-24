package org.base.repository;


import org.base.repository.impls.InfluxdbBookRepository;
import org.base.repository.impls.MongodbBookRepository;
import org.base.repository.impls.RedisBookRepository;
import org.base.repository.impls.SqlBookRepository;

public class BookFactory {


	public static BookRepository getRepository(int databaseType) {


		return switch (databaseType) {
			case 1 -> new SqlBookRepository();
			case 2 -> new MongodbBookRepository();
			case 3 -> new RedisBookRepository();
			case 4 -> new InfluxdbBookRepository();
			default -> throw new IllegalArgumentException("Hệ thống chưa hỗ trợ loại Database: " + databaseType);
		};
	}
}