package com.example.sms_lemadio_sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SMSWorker extends Service {

    private static final String TAG = "SMSService";
    private volatile boolean isRunning = false;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        executorService.submit(this::processSmsTasks);
        startForeground(1, createNotification());
        return START_STICKY;
    }

    private void processSmsTasks() {
        while (isRunning) {
            try {
                if (isInternetAvailable()) {
                    List<ClientInfo> unsentClients = recupererClientsNonTraites();
                    if (!unsentClients.isEmpty()) {

                        // Récupération de la région depuis SharedPreferences
                        String zone = getRegionFromSession();
                        if (zone.isEmpty()) {
                            Log.e(TAG, "Aucune région trouvée dans la session. Annulation du traitement.");
                            break;
                        }

                        for (ClientInfo client : unsentClients) {
                            if(zone.equals(client.region)) {
                                envoyerSMS(client);
                            }
                        }
                    } else {
                        Thread.sleep(10_000);
                    }
                } else {
                    Log.d(TAG, "Pas de connexion Internet disponible. En attente...");
                    Thread.sleep(10_000);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interruption du processus", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors du traitement des SMS", e);
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
        Log.d(TAG, "Service arrêté.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return false;
    }

    private List<ClientInfo> recupererClientsNonTraites() {
        List<ClientInfo> unsentClients = new ArrayList<>();
        try {
            URL url = new URL(ApiUrl.API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            if (connection.getResponseCode() >= 200 && connection.getResponseCode() <= 299) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if ("success".equals(jsonResponse.getString("status"))) {
                        JSONArray clientsArray = jsonResponse.getJSONArray("data");
                        for (int i = 0; i < clientsArray.length(); i++) {
                            JSONObject clientJson = clientsArray.getJSONObject(i);
                            if (!clientJson.optBoolean("is_sms_sent", false)) {
                                ClientInfo client = new ClientInfo();
                                client.name = clientJson.getString("client_name");
                                client.phoneNumber = clientJson.getString("client_phone");
                                client.invoiceNumber = clientJson.getString("invoice_number");
                                client.stove = clientJson.getString("stove");
                                client.consultation = clientJson.getString("consultation_url");
                                client.region = clientJson.getString("saleSite");
                                unsentClients.add(client);
                                Log.d("List", "Nom: "+client.name+"\nTel: "+client.phoneNumber+" smsSent: "+client.smsSent+" Region: "+client.region);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur de récupération des données", e);
        }
        return unsentClients;
    }

    private void envoyerSMS(ClientInfo client) {
        try {
            String numero = formaterNumero(client.phoneNumber);
            if (!validerNumeroMadagascar(numero)) {
                Log.e(TAG, "Numéro invalide : " + numero);
                mettreAJourStatutSMS(client.stove, false);
                return;
            }

            String message = String.format(
                    "Bonjour %s! Nous vous remercions pour votre achat chez ADES. Vous avez acquis un réchaud modèle %s. Votre numéro de facture est : %s. Pour consulter les détails de votre garantie, veuillez cliquer sur ce lien : %s. Nous vous remercions pour votre confiance!",
                    client.name,
                    client.stove,
                    client.invoiceNumber,
                    "http://ades.mg/" + client.consultation
            );

            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(numero, null, parts, null, null);

            Log.d(TAG, "SMS envoyé à : " + numero);
            mettreAJourStatutSMS(client.stove, true);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'envoi du SMS", e);
            mettreAJourStatutSMS(client.stove, false);
        }
    }

    private void mettreAJourStatutSMS(String stove, boolean smsSent) {
        try {
            URL url = new URL(ApiUrl.API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("stove", stove);
            jsonPayload.put("sms_sent", smsSent);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode <= 299) {
                Log.d(TAG, "Statut SMS mis à jour avec succès pour le poêle : " + stove);
            } else {
                Log.e(TAG, "Échec de la mise à jour du statut SMS. Code de réponse : " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la mise à jour du statut SMS", e);
        }
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                "SMSServiceChannel",
                "SMS Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
        return new Notification.Builder(this, "SMSServiceChannel")
                .setContentTitle("Service de gestion des SMS")
                .setContentText("Le service fonctionne en arrière-plan.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }

    private static String formaterNumero(String numero) {
        numero = numero.replaceAll("\\s", "").replace("-", "");

        if (numero.startsWith("34") || numero.startsWith("32") ||
                numero.startsWith("33") || numero.startsWith("38") || numero.startsWith("37")) {
            numero = "+261" + numero;
        }

        if (!numero.startsWith("+261")) {
            numero = "+261" + numero;
        }

        return numero;
    }

    private static boolean validerNumeroMadagascar(String numero) {
        return numero.matches("^\\+261[0-9]{9}$") ||
                numero.matches("^00261[0-9]{9}$");
    }

    //Recuperer la région
    private String getRegionFromSession() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
        return sharedPreferences.getString("selectedRegion", ""); // Retourne une chaîne vide si aucune région n'est trouvée
    }

}

