package com.example.sms_lemadio_sender;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static ApiClient instance;
    private final ApiService apiService;
    private final Context context;

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();

        // Création du logging interceptor pour le debug
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Configuration du client OkHttp avec tous les intercepteurs
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                // Intercepteur pour les headers par défaut
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .method(original.method(), original.body());
                    return chain.proceed(builder.build());
                })
                // Intercepteur pour l'authentification
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    SharedPreferences prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
                    String token = prefs.getString("access_token", null);

                    if (token != null) {
                        Request.Builder builder = original.newBuilder()
                                .header("Authorization", "Bearer " + token);
                        return chain.proceed(builder.build());
                    }
                    return chain.proceed(original);
                })
                .build();

        // Configuration de Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiUrl.SMS_USER_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }
}