package com.example.sms_lemadio_sender;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/auth/loginSmsUser/")
    Call<LoginResponse> login(@Body LoginRequest loginRequest);
}