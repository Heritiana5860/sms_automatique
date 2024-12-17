package com.example.sms_lemadio_sender;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/*public class SMSWorkManagerScheduler {
    private static final long RETRY_INTERVAL = 10; // 10 secondes

    public static void planifierTravail(Context context) {
        // Définir des contraintes de réseau
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Utiliser OneTimeWorkRequest pour un déclenchement immédiat
        OneTimeWorkRequest smsWork = new OneTimeWorkRequest.Builder(SMSWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        RETRY_INTERVAL,
                        TimeUnit.SECONDS
                )
                .build();

        // Enqueue le travail immédiatement
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "sms_sync_work",
                        ExistingWorkPolicy.REPLACE,
                        smsWork
                );
    }

    // Méthode pour planifier des vérifications répétées
    public static void planifierVerificationsContinues(Context context) {
        // Crée un WorkRequest périodique avec un intervalle court
        PeriodicWorkRequest rapidVerification =
                new PeriodicWorkRequest.Builder(SMSWorker.class, 10, TimeUnit.SECONDS)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "rapid_sms_verification",
                        ExistingPeriodicWorkPolicy.KEEP,
                        rapidVerification
                );
    }
}
*/