package com.example.sms_lemadio_sender;

import static com.example.sms_lemadio_sender.MainActivity.TAG;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FetchClientsTask extends AsyncTask<Void, Void, List<ClientInfo>> {
    private static final String API_URL = "http://10.85.5.165:8000/api/get_client_sales_info/";
    private List<ClientInfo> clientList = new ArrayList<>();
    private static Set<String> processedInvoices = new HashSet<>();
    private Context context;

    // Constructeur pour passer le contexte
    public FetchClientsTask(Context context) {
        this.context = context;
    }

    @Override
    protected List<ClientInfo> doInBackground(Void... voids) {
        List<ClientInfo> newClients = new ArrayList<>();
        try {
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response Code: " + responseCode);

            BufferedReader reader = responseCode >= 200 && responseCode <= 299
                    ? new BufferedReader(new InputStreamReader(connection.getInputStream()))
                    : new BufferedReader(new InputStreamReader(connection.getErrorStream()));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            Log.d(TAG, "Full Response: " + response.toString());

            JSONObject jsonResponse = new JSONObject(response.toString());
            if ("success".equals(jsonResponse.getString("status"))) {
                JSONArray clientsArray = jsonResponse.getJSONArray("data");

                for (int i = 0; i < clientsArray.length(); i++) {
                    JSONObject clientJson = clientsArray.getJSONObject(i);

                    // Vérifier explicitement is_sms_sent
                    boolean isSMSSent = clientJson.optBoolean("is_sms_sent", false);

                    if (!isSMSSent) {
                        ClientInfo client = new ClientInfo();
                        client.name = clientJson.getString("client_name");
                        client.phoneNumber = clientJson.getString("client_phone");
                        client.invoiceNumber = clientJson.getString("invoice_number");
                        client.stove = clientJson.getString("stove");
                        client.consultation = clientJson.getString("consultation_url");
                        client.smsSent = false;

                        newClients.add(client);
                    }
                }
            } else {
                Log.e(TAG, "Statut API non success");
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur de récupération des données", e);
        }
        return newClients;
    }

    @Override
    protected void onPostExecute(List<ClientInfo> newClients) {
        if (!newClients.isEmpty()) {
            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;

                // Ajouter les nouveaux clients à la liste principale de l'activité
                if (mainActivity.clientList == null) {
                    mainActivity.clientList = new ArrayList<>();
                }

                // Ajouter uniquement les nouveaux clients
                mainActivity.clientList.addAll(newClients);

                // Vérifier les conditions d'envoi
                if (mainActivity.verifierConditionsEnvoi()) {
                    mainActivity.sendNextSMS();
                } else {
                    Toast.makeText(context,
                            "Conditions réseau non remplies",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.d(TAG, "Aucun nouveau client trouvé");
        }
    }
}
