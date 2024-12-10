package com.example.sms_lemadio_sender;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SMS_SENDER";
    private static final int PERMISSION_REQUEST_SEND_SMS = 123;
    private EditText editTextNumero;
    private EditText editTextMessage;
    private Button btnEnvoyerSMS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des vues
        editTextNumero = findViewById(R.id.editTextNumero);
        editTextMessage = findViewById(R.id.editTextMessage);
        btnEnvoyerSMS = findViewById(R.id.btnEnvoyerSMS);

        // Configuration du bouton d'envoi
        btnEnvoyerSMS.setOnClickListener(v -> {
            String numero = editTextNumero.getText().toString().trim();
            String message = editTextMessage.getText().toString().trim();

            if (TextUtils.isEmpty(numero) || TextUtils.isEmpty(message)) {
                afficherErreur("Veuillez saisir un numéro et un message.");
                return;
            }

            verifierEtEnvoyerSMS(numero, message);
        });
    }

    private void verifierEtEnvoyerSMS(String numero, String message) {
        if (!verifierConditionsEnvoi()) {
            return;
        }

        numero = formaterNumero(numero);

        if (!validerNumeroMadagascar(numero)) {
            afficherErreur("Numéro invalide : " + numero);
            return;
        }

        envoyerSMSMultiMethode(numero, message);
    }

    private boolean verifierConditionsEnvoi() {
        // Vérification de la permission SMS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            demanderPermissionSMS();
            return false;
        }

        // Vérification de la connectivité réseau et du mode avion
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                afficherDiagnosticConnexion("Mode avion activé. Veuillez le désactiver.");
                return false;
            }

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                afficherDiagnosticConnexion("Pas de connexion réseau disponible.");
                return false;
            }
        }

        // Vérification de la carte SIM
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            afficherErreur("Carte SIM non disponible ou incorrectement insérée.");
            return false;
        }

        return true;
    }

    private void envoyerSMSMultiMethode(String numero, String message) {
        try {
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
            Toast.makeText(this, "SMS envoyé avec succès.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'envoi du SMS", e);
            afficherErreur("Échec de l'envoi : " + e.getMessage());
        }
    }

    private PendingIntent creerPendingIntent(String action) {
        Intent intent = new Intent(action);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private String formaterNumero(String numero) {
        numero = numero.replaceAll("\\s", "").replace("-", "");
        if (numero.startsWith("03")) {
            numero = "+261" + numero.substring(1);
        }
        return numero;
    }

    private boolean validerNumeroMadagascar(String numero) {
        return numero.matches("^\\+261[32347]\\d{8}$");
    }

    private void demanderPermissionSMS() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_REQUEST_SEND_SMS);
    }

    private void afficherErreur(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void afficherDiagnosticConnexion(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Problème de Réseau")
                .setMessage(message)
                .setPositiveButton("Paramètres", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_SEND_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String numero = editTextNumero.getText().toString().trim();
                String message = editTextMessage.getText().toString().trim();
                verifierEtEnvoyerSMS(numero, message);
            } else {
                afficherErreur("Permission refusée pour envoyer des SMS.");
            }
        }
    }
}
