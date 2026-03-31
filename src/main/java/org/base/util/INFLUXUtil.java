package org.base.util;

import com.influxdb.client.*;
import com.influxdb.client.domain.HealthCheck;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class INFLUXUtil {
    private static InfluxDBClient client;
    private static String org;
    private static String bucket;

    public static synchronized void init(String url, String token, String organization, String bucketName) {
        if (client != null) {
            System.out.println("InfluxDB đã được khởi tạo từ trước.");
            return;
        }

        try {
            org = organization;
            bucket = bucketName;

            OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                    .readTimeout(3, TimeUnit.MINUTES)
                    .writeTimeout(3, TimeUnit.MINUTES)
                    .connectTimeout(3, TimeUnit.MINUTES);

            InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                    .url(url)
                    .authenticateToken(token.toCharArray())
                    .org(org)
                    .bucket(bucket)
                    .okHttpClient(httpClient)
                    .build();

            client = InfluxDBClientFactory.create(options);

            HealthCheck health = client.health();
            if (health.getStatus() == HealthCheck.StatusEnum.PASS) {
                System.out.println("[OK] Đã kết nối InfluxDB thành công.");
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

    public static WriteApiBlocking getWriteApiBlocking() {
        return getClient().getWriteApiBlocking();
    }

     public static WriteApi getWriteApi() {
         return getClient().makeWriteApi();
     }

    public static String getOrg() { return org; }
    public static String getBucket() { return bucket; }

    public static void close() {
        if (client != null) {
            client.close();
            client = null;
            System.out.println("Đã đóng kết nối InfluxDB.");
        }
    }
}