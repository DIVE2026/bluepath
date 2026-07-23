package com.bluepath.app.network;

import com.bluepath.app.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static volatile BluePathApi service;

    private ApiClient() {}

    public static boolean isConfigured() {
        return BuildConfig.BLUEPATH_API_BASE_URL != null
                && !BuildConfig.BLUEPATH_API_BASE_URL.trim().isEmpty();
    }

    public static BluePathApi service() {
        if (service == null) {
            synchronized (ApiClient.class) {
                if (service == null) {
                    OkHttpClient httpClient = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(45, TimeUnit.SECONDS)
                            .readTimeout(360, TimeUnit.SECONDS)
                            .callTimeout(390, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(normalizeBaseUrl(BuildConfig.BLUEPATH_API_BASE_URL))
                            .client(httpClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    service = retrofit.create(BluePathApi.class);
                }
            }
        }
        return service;
    }

    public static String resolveMediaUrl(String value) {
        String media = value == null ? "" : value.trim();
        if (media.isEmpty()) return "";
        int uploadsIndex = media.indexOf("/uploads/");
        if (uploadsIndex >= 0) media = media.substring(uploadsIndex + 1);
        if (media.startsWith("http://") || media.startsWith("https://")) return media;
        while (media.startsWith("/")) media = media.substring(1);
        return normalizeBaseUrl(BuildConfig.BLUEPATH_API_BASE_URL) + media;
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null ? "" : value.trim();
        if (base.isEmpty()) base = "http://127.0.0.1/";
        if (!base.endsWith("/")) base += "/";
        return base;
    }
}
