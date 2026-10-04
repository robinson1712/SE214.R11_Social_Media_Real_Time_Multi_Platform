package com.example.ui_mobile.api;

import android.content.Context;

import com.example.ui_mobile.BuildConfig;
import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static ApiService service;

    private ApiClient() {
    }

    public static synchronized ApiService service(Context context) {
        if (service == null) {
            SessionStore session = new SessionStore(context);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token = session.accessToken();
                        if (token != null && !request.url().encodedPath().startsWith("/api/auth/")) {
                            request = request.newBuilder()
                                    .header("Authorization", "Bearer " + token)
                                    .build();
                        }
                        return chain.proceed(request);
                    })
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
            service = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }

    public static String errorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                ApiResponse<?> body = new Gson().fromJson(response.errorBody().string(), ApiResponse.class);
                if (body != null && body.message != null && !body.message.isEmpty()) {
                    return body.message;
                }
            }
        } catch (Exception ignored) {
        }
        return "Lỗi máy chủ (" + response.code() + ")";
    }
}
