package com.example.sms_lemadio_sender;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Arrays;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private AutoCompleteTextView citySpinner;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    String[] cities = {"Manakara", "Fianarantsoa", "Toliara", "Antananarivo"};
    String selectedRegion;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        citySpinner = findViewById(R.id.citySpinner);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        // Setup city spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.simple_dropdown_item_1line,
                cities
        );
        citySpinner.setAdapter(adapter);

        // Utiliser le bon listener pour AutoCompleteTextView
        citySpinner.setOnItemClickListener((parent, view, position, id) -> {
            selectedRegion = parent.getItemAtPosition(position).toString();
            Log.d("SelectedCity", "Ville sélectionnée : " + selectedRegion);
        });

        // Ajouter un TextWatcher pour gérer la saisie manuelle
        citySpinner.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String enteredText = s.toString();
                if (Arrays.asList(cities).contains(enteredText)) {
                    selectedRegion = enteredText;
                } else {
                    selectedRegion = null;
                }
            }
        });

        btnLogin.setOnClickListener(v -> performLogin());

    }

    private void performLogin() {
        String username = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        Log.d("LOGIN_DEBUG", "Username: " + username);

        // Validation des champs
        if (username.isEmpty()) {
            etEmail.setError("Le nom d'utilisateur est requis");
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Le mot de passe est requis");
            return;
        }

        // Afficher le ProgressBar
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        LoginRequest loginRequest = new LoginRequest(username, password);
        Call<LoginResponse> call = ApiClient.getInstance(this).getApiService().login(loginRequest);

        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                // Masquer le ProgressBar
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Log.d("LOGIN_DEBUG", "Response code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    saveRegionToSession(selectedRegion);
                    saveTokens(response.body());
                    startMainActivity();
                } else {
                    try {
                        JSONObject errorBody = new JSONObject(response.errorBody().string());
                        String errorMessage = errorBody.getString("error");
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.d("LOGIN_DEBUG", "Error body: " + response.errorBody().string());
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Erreur de connexion", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                // Masquer le ProgressBar
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this,
                        "Erreur réseau : " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveTokens(LoginResponse loginResponse) {
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("access_token", loginResponse.getAccess());
        editor.putString("refresh_token", loginResponse.getRefresh());
        editor.apply();
    }

    private void startMainActivity() {
        String selectedRegion = getRegionFromSession();
        Intent intent = new Intent(this, MainActivity2.class);
        intent.putExtra("selectedRegion", selectedRegion);
        startActivity(intent);
        finish();
    }

    //Session
    private void saveRegionToSession(String region) {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("selectedRegion", region);
        editor.apply();
        Log.d("LOGIN_DEBUG", "Région sauvegardée : " + region);
    }

    private String getRegionFromSession() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        return sharedPreferences.getString("selectedRegion", null); // Récupère la région
    }

    private void clearSession() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear(); // Supprime toutes les données de la session
        editor.apply();
    }

}