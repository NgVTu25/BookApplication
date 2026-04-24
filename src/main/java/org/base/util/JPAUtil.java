package org.base.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

public class JPAUtil {
	private static EntityManagerFactory emf;

	public static void init(String url, String username, String password) {
		if (emf == null || !emf.isOpen()) {

			Map<String, Object> props = new HashMap<>();

			props.put("jakarta.persistence.jdbc.url", url);
			props.put("jakarta.persistence.jdbc.user", username);
			props.put("jakarta.persistence.jdbc.password", password);

			props.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");

			props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
			props.put("hibernate.hbm2ddl.auto", "update");
			props.put("hibernate.show_sql", "false");

			emf = Persistence.createEntityManagerFactory("sqldb", props);
		}
	}

	public static EntityManager getEntityManager() {
		if (emf == null) {
			throw new IllegalStateException("JPA chưa được khởi tạo. Hãy gọi JPAUtil.init()");
		}
		return emf.createEntityManager();
	}

	public static void close() {
		if (emf != null && emf.isOpen()) {
			emf.close();
		}
	}
}