package com.example.sms_lemadio_sender;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

public class BackgroundSMSService extends Service {
    private static final String TAG = "BackgroundSMSService";
    private Handler periodicUpdateHandler;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final long RETRY_INTERVAL = 300000; // 5 minutes if conditions not met

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Start periodic updates with retry mechanism
        startPeriodicUpdates();

        // The service continues running until explicitly stopped
        return START_STICKY;
    }

    private void startPeriodicUpdates() {
        periodicUpdateHandler = new Handler(Looper.getMainLooper());
        periodicUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                // Check sending conditions
                if (verifierConditionsEnvoi()) {
                    FetchClientsTask task = new FetchClientsTask(BackgroundSMSService.this);
                    task.execute();

                    // Schedule next update at normal interval
                    periodicUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
                } else {
                    // If conditions not met, retry after a longer interval
                    periodicUpdateHandler.postDelayed(this, RETRY_INTERVAL);
                    Log.d(TAG, "Sending conditions not met. Retrying in 5 minutes.");
                }
            }
        });
    }

    private boolean verifierConditionsEnvoi() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            // Check airplane mode
            if (Settings.Global.getInt(getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                Log.e(TAG, "Airplane mode is on");
                return false;
            }

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                Log.e(TAG, "No network connection");
                return false;
            }
        }

        // Check SIM card
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            Log.e(TAG, "SIM card not available");
            return false;
        }

        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop periodic updates
        if (periodicUpdateHandler != null) {
            periodicUpdateHandler.removeCallbacksAndMessages(null);
        }
    }
}