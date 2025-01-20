package com.example.sms_lemadio_sender;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity2 extends AppCompatActivity {

    private PieChart pieChart;
    private TextView totalSmsText, sentSmsText, pendingSmsText, region;
    private final Handler handler = new Handler();
    private Runnable updateRunnable;
    private static final int PERMISSION_REQUEST_SEND_SMS = 1;
    private TextView tvUserEmail, tvSelectedCity;
    private MaterialButton btnLogout;
    String selectedRegion;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);

        // Initialize views
        pieChart = findViewById(R.id.pieChart);
        totalSmsText = findViewById(R.id.totalSmsText);
        sentSmsText = findViewById(R.id.sentSmsText);
        pendingSmsText = findViewById(R.id.pendingSmsText);
        region = findViewById(R.id.zone);

        // Configure pieChart
        setupPieChart();

        // Start periodic updates
        startPeriodicUpdates();

        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logout();
            }
        });

        // Récupération de la région transmise
        String zone = getRegionFromSession();
        region.setText("Zone: " + zone );

    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(61f);

        // Activer et configurer la légende
        Legend legend = pieChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setDrawInside(false);
        legend.setTextSize(12f);
        legend.setWordWrapEnabled(true);
        legend.setMaxSizePercent(0.5f); // La légende prendra maximum 50% de la largeur du graphique

        // Désactiver les labels sur le graphique lui-même
        pieChart.setDrawEntryLabels(false);
    }

    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDashboard();
                handler.postDelayed(this, 30000); // Update every 30 seconds
            }
        };
        handler.post(updateRunnable);
    }

    private void updateDashboard() {
        new Thread(() -> {
            try {
                URL url = new URL(ApiUrl.API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if ("success".equals(jsonResponse.getString("status"))) {
                        JSONArray data = jsonResponse.getJSONArray("data");
                        updateStats(data);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateStats(JSONArray data) {
        try {
            int totalSms = data.length();
            int sentSms = 0;
            Map<String, Integer> stoveTypeCount = new HashMap<>();

            for (int i = 0; i < data.length(); i++) {
                JSONObject client = data.getJSONObject(i);
                if (client.getBoolean("is_sms_sent")) {
                    sentSms++;
                }
                String stove = client.getString("stove");

                // Séparer le type et le numéro
                String[] stoveParts = stove.split(" ");
                if (stoveParts.length > 0) {
                    String stoveType = stoveParts[0]; // Le type du réchaud
                    stoveTypeCount.put(stoveType, stoveTypeCount.getOrDefault(stoveType, 0) + 1);
                }
            }

            final int finalSentSms = sentSms;
            runOnUiThread(() -> {
                totalSmsText.setText("Total SMS: " + totalSms);
                sentSmsText.setText("SMS envoyés: " + finalSentSms);
                pendingSmsText.setText("SMS en attente: " + (totalSms - finalSentSms));

                updatePieChart(stoveTypeCount);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePieChart(Map<String, Integer> stoveCount) {
        ArrayList<PieEntry> entries = new ArrayList<>();

        // Trier les entrées par valeur décroissante
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(stoveCount.entrySet());
        Collections.sort(sortedEntries, (a, b) -> b.getValue().compareTo(a.getValue()));

        // Limiter le nombre d'entrées affichées si nécessaire
        int maxEntries = 15; // Nombre maximum d'entrées à afficher
        int otherCount = 0;

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, Integer> entry = sortedEntries.get(i);
            if (i < maxEntries) {
                // Ajouter les entrées avec type et nombre total dans l'étiquette
                String label = entry.getKey() + " = " + entry.getValue();
                entries.add(new PieEntry(entry.getValue(), label));
            } else {
                otherCount += entry.getValue();
            }
        }

        // Ajouter une entrée "Autres" si nécessaire
        if (otherCount > 0) {
            entries.add(new PieEntry(otherCount, "Autres = " + otherCount));
        }

        // Créer et configurer le jeu de données
        PieDataSet dataSet = new PieDataSet(entries, "Modèles de réchauds");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.WHITE);

        // Configurer le format des valeurs pour afficher le pourcentage
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f%%", value); // Pourcentage formaté
            }
        });

        // Configurer les données et rafraîchir le graphique
        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate(); // Rafraîchir le graphique pour afficher les changements
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
    }
    //Recuperer la région
    private String getRegionFromSession() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        return sharedPreferences.getString("selectedRegion", ""); // Retourne une chaîne vide si aucune région n'est trouvée
    }

    //Se deconnecter
    private void clearSession() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    private void logout() {
        clearSession(); // Supprimer la session
        Intent intent = new Intent(MainActivity2.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

}