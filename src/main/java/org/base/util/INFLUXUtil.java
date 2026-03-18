package org.base.util;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.HealthCheck;


public class INFLUXUtil {
    private static InfluxDBClient client;
    private static String org;
    private static String bucket;


    public static void init(String url, String token, String organization, String bucketName) {
        try {
            client = InfluxDBClientFactory.create(url, token.toCharArray(), organization, bucketName);
            org = organization;
            bucket = bucketName;

            HealthCheck health = client.health();
            if (health != null && health.getStatus() == HealthCheck.StatusEnum.PASS) {
                System.out.println("[OK] Đã kết nối InfluxDB.");
            } else {
                System.err.println("[LỖI] Không thể ping tới InfluxDB.");
            }
        } catch (Exception e) {
            System.err.println("[LỖI] Khởi tạo InfluxDB thất bại: " + e.getMessage());
        }
    }

    public static InfluxDBClient getClient() {
        if (client == null) throw new IllegalStateException("INFLUXUtil chưa được init!");
        return client;
    }

    public static WriteApiBlocking getWriteApi() {
        return getClient().getWriteApiBlocking();
    }

    public static String getOrg() { return org; }
    public static String getBucket() { return bucket; }

    public static void close() {
        if (client != null) {
            client.close();
            System.out.println("Đã đóng kết nối InfluxDB.");
        }
    }
}