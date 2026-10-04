package com.example.ui_mobile.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("api/auth/login")
    Call<ApiResponse<AuthData>> login(@Body LoginBody body);

    @POST("api/auth/register")
    Call<ApiResponse<AuthData>> register(@Body RegisterBody body);
}
