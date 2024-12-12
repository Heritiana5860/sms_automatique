package com.example.sms_lemadio_sender;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "SMS_SENDER";
    private static final String API_URL = "http://10.85.5.165:8000/api/get_client_sales_info/";
    public List<ClientInfo> clientList = new ArrayList<>();
    private Handler periodicUpdateHandler;
    private static final long UPDATE_INTERVAL = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupSMSStatusReceivers();

        // Vérifier les permissions avant de commencer
        if (checkSMSPermission()) {
            startPeriodicUpdates();
        }
    }

    private void startPeriodicUpdates() {
        periodicUpdateHandler = new Handler(Looper.getMainLooper());
        periodicUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                // Récupérer les nouvelles données à chaque intervalle
                if (verifierConditionsEnvoi()) {
                    FetchClientsTask task = new FetchClientsTask(MainActivity.this);
                    task.execute();
                }

                // Reprogrammer la prochaine mise à jour
                periodicUpdateHandler.postDelayed(this, UPDATE_INTERVAL*2);
            }
        });
    }

    private boolean checkSMSPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    123);
            return false;
        }
        Log.d(TAG, "Permission SEND_SMS accordée: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED));
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 123 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new FetchClientsTask(this).execute();
        }
    }

    public void sendNextSMS() {
        if (clientList == null || clientList.isEmpty()) {
            Log.d(TAG, "Aucun client à traiter");
            return;
        }

        // Trouver le premier client non traité
        ClientInfo currentClient = clientList.stream()
                .filter(client -> !client.smsSent)
                .findFirst()
                .orElse(null);

        if (currentClient == null) {
            Log.d(TAG, "Tous les clients ont été traités");
            return;
        }

        String numero = formaterNumero(currentClient.phoneNumber);

        if (!validerNumeroMadagascar(numero)) {
            Log.e(TAG, "Numéro invalide : " + numero);
            currentClient.smsSent = true;
            updateSMSSentStatus(currentClient.invoiceNumber);
            return;
        }

        String message = String.format(
                "Bonjour %s! Nous vous remercions pour votre achat chez ADES. Vous avez acquis un réchaud modèle %s. Votre numéro de facture est : %s. Pour consulter les détails de votre garantie, veuillez cliquer sur ce lien : %s. Nous vous remercions pour votre confiance!",
                currentClient.name,
                currentClient.stove,
                currentClient.invoiceNumber,
                "http://ades.mg/" + currentClient.consultation
        );

        envoyerSMSMultiMethode(numero, message);
        currentClient.smsSent = true;
        updateSMSSentStatus(currentClient.stove);
    }

    private void updateSMSSentStatus(String stove) {
        RequestQueue queue = Volley.newRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("stove", stove);
        } catch (JSONException e) {
            Log.e(TAG, "Erreur JSON", e);
            return;
        }


        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                API_URL,
                payload,
                response -> {
                    try {
                        String status = response.getString("status");
                        if ("success".equals(status)) {
                            Log.d(TAG, "Statut SMS mis à jour pour " + stove);
                        } else {
                            Log.e(TAG, "Échec mise à jour statut SMS: " + response.toString());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Erreur parsing réponse", e);
                    }
                },
                error -> {
                    Log.e(TAG, "Erreur réseau mise à jour statut SMS", error);
                    // Optional: Implement retry mechanism
                }
        );

        // Set a longer timeout and retry policy
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000,  // 20 seconds timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(jsonObjectRequest);
    }

    public boolean verifierConditionsEnvoi() {
        // Vérification de la connectivité réseau et du mode avion
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Settings.Global.getInt(getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                Log.e(TAG, "Mode avion activé");
                return false;
            }

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                Log.e(TAG, "Pas de connexion réseau");
                return false;
            }
        }

        // Vérification de la carte SIM
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            Log.e(TAG, "Carte SIM non disponible");
            return false;
        }

        return true;
    }

    private void envoyerSMSMultiMethode(String numero, String message) {
        try {

            Log.d(TAG, "Attempting to send SMS to: " + numero);
            Log.d(TAG, "Message content: " + message);

            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);

            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(creerPendingIntent("SMS_SENT_" + i));
                deliveryIntents.add(creerPendingIntent("SMS_DELIVERED_" + i));
            }

            smsManager.sendMultipartTextMessage(numero, null, parts, sentIntents, deliveryIntents);
            Log.d(TAG, "SMS envoyé à : " + numero);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'envoi du SMS", e);
        }
    }

    private PendingIntent creerPendingIntent(String action) {
        Intent intent = new Intent(action);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private String formaterNumero(String numero) {
        // Log the original number for debugging
        Log.d(TAG, "Original number: " + numero);

        // Remove all spaces and hyphens
        numero = numero.replaceAll("\\s", "").replace("-", "");

        // Special handling for Madagascar numbers
        if (numero.startsWith("34") || numero.startsWith("32") ||
                numero.startsWith("33") || numero.startsWith("38")) {
            numero = "+261" + numero;
        }

        // Ensure the number starts with +261
        if (!numero.startsWith("+261")) {
            numero = "+261" + numero;
        }

        Log.d(TAG, "Formatted number: " + numero);
        return numero;
    }

    private boolean validerNumeroMadagascar(String numero) {
        // More flexible validation
        boolean isValid = numero.matches("^\\+261[0-9]{9}$") ||
                numero.matches("^00261[0-9]{9}$");
        Log.d(TAG, "Number validation: " + numero + " is " + (isValid ? "VALID" : "INVALID"));
        return isValid;
    }

    private void setupSMSStatusReceivers() {
        BroadcastReceiver smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String numero = intent.getStringExtra("phone_number");
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "SMS sent successfully to " + numero);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e(TAG, "Generic failure sending SMS to " + numero);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e(TAG, "No service when sending SMS to " + numero);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e(TAG, "Radio off when sending SMS to " + numero);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e(TAG, "Null PDU when sending SMS to " + numero);
                        break;
                }
            }
        };

        BroadcastReceiver smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String numero = intent.getStringExtra("phone_number");
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "SMS delivered successfully to " + numero);
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e(TAG, "SMS not delivered to " + numero);
                        break;
                }
            }
        };

        registerReceiver(smsSentReceiver, new IntentFilter("SMS_SENT"));
        registerReceiver(smsDeliveredReceiver, new IntentFilter("SMS_DELIVERED"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Arrêter les mises à jour périodiques
        if (periodicUpdateHandler != null) {
            periodicUpdateHandler.removeCallbacksAndMessages(null);
        }
    }
}
