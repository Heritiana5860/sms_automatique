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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SMS_SENDER";
    private static final String API_URL = "http://10.85.5.165:8000/api/get_client_sales_info/";
    private List<ClientInfo> clientList = new ArrayList<>();
    private int currentClientIndex = 0;

    private static class ClientInfo {
        String name;
        String phoneNumber;
        String invoiceNumber;
        boolean smsSent = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupSMSStatusReceivers();

        // Vérifier les permissions avant de commencer
        if (checkSMSPermission()) {
            new FetchClientsTask().execute();
        }
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
            new FetchClientsTask().execute();
        }
    }

    private class FetchClientsTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // Vérifier le code de réponse HTTP
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response Code: " + responseCode);

                // Lire la réponse
                BufferedReader reader;
                if (responseCode >= 200 && responseCode <= 299) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Log de la réponse complète
                Log.d(TAG, "Full Response: " + response.toString());

                // Vérifier si la réponse contient des données
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray clientsArray = jsonResponse.getJSONArray("data");

                if (clientsArray.length() == 0) {
                    return "Aucune donnée trouvée";
                }

                for (int i = 0; i < clientsArray.length(); i++) {
                    JSONObject clientJson = clientsArray.getJSONObject(i);
                    ClientInfo client = new ClientInfo();

                    Log.d(TAG, "Client Data: " +
                            "Name: " + clientJson.getString("name") +
                            ", Phone: " + clientJson.getString("phone_number") +
                            ", Invoice: " + clientJson.getString("number_of_Invoice")
                    );

                    client.name = clientJson.getString("name");
                    client.phoneNumber = clientJson.getString("phone_number");
                    client.invoiceNumber = clientJson.getString("number_of_Invoice");
                    clientList.add(client);
                }

                return "success";
            } catch (Exception e) {
                // Log détaillé de l'erreur
                Log.e(TAG, "Erreur de récupération des données", e);
                return "Erreur : " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("success")) {
                if (verifierConditionsEnvoi()) {
                    sendNextSMS();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Conditions réseau non remplies",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(MainActivity.this,
                        "Échec : " + result,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendNextSMS() {
        if (currentClientIndex >= clientList.size()) {
            Log.d(TAG, "All SMS sending completed");
            Toast.makeText(this, "Envoi des SMS terminé", Toast.LENGTH_LONG).show();
            return;
        }

        ClientInfo currentClient = clientList.get(currentClientIndex);
        /*String message = String.format(
                "Bonjour %s, votre facture numéro %s est prête. Merci de nous contacter.",
                currentClient.name,
                currentClient.invoiceNumber
        );*/

        Log.d(TAG, "Processing client: " + currentClient.name);
        Log.d(TAG, "Original phone number: " + currentClient.phoneNumber);

        String message = String.format(
                "Bonjour %s!, vous aves acheté ce réchaud %s chez ADES.",
                currentClient.name,
                currentClient.invoiceNumber
        );

        String numero = formaterNumero(currentClient.phoneNumber);
        Log.d(TAG, "Formatted phone number: " + numero);

        if (validerNumeroMadagascar(numero)) {
            Log.d(TAG, "Envoi du SMS à: " + numero + " avec message: " + message);
            envoyerSMSMultiMethode(numero, message);
            currentClient.smsSent = true;
            currentClientIndex++;

            // Délai entre les SMS pour éviter la surcharge
            new Handler().postDelayed(this::sendNextSMS, 5000);
        } else {
            Log.e(TAG, "Numéro invalide : " + numero);
            currentClientIndex++;
            sendNextSMS(); // Passer au client suivant
        }
    }

    private boolean verifierConditionsEnvoi() {
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
}
