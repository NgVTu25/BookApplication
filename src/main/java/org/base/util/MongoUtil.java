package org.base.util;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


public class MongoUtil {
	private static MongoClient mongoClient;
	private static MongoDatabase database;

	public static void init(String connectionString, String dbName) {
		if (mongoClient == null) {
			mongoClient = MongoClients.create(connectionString);
			database = mongoClient.getDatabase(dbName);
		}
	}

	public static MongoCollection<Document> getCollection(String collectionName) {
		if (database == null) {
			throw new IllegalStateException("MongoDB chưa được khởi tạo. Cần gọi MongoUtil trước!");
		}
		return database.getCollection(collectionName);
	}

	public static void close() {
		if (mongoClient != null) {
			mongoClient.close();
		}
	}
}