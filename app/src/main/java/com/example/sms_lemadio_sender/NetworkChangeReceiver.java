package com.example.sms_lemadio_sender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Check for both connectivity change and boot completed
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action) ||
                Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager != null) {
                NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

                if (activeNetwork != null && activeNetwork.isConnected()) {
                    // Start the background service
                    Intent serviceIntent = new Intent(context, BackgroundSMSService.class);
                    context.startService(serviceIntent);
                    Log.d(TAG, "Service started due to network connection or boot");
                }
            }
        }
    }
}