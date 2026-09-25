package com.example.bruxismdetector;

import android.util.Base64;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ArduinoOTA {
    private static final String TAG = "ArduinoOTA_Debug";

    // Configurabilità dei parametri di rete e autenticazione
    private int port = 65280;
    private int connectTimeout = 5000;
    private int readTimeout = 90000;
    private String username = "arduino";
    private String password = "";

    // Interfaccia per monitorare avanzamento e log in tempo reale nell'UI dell'app
    public interface ProgressListener {
        void onProgress(int progressPercent, long bytesSent, long totalBytes);
        void onLog(String message);
    }

    private ProgressListener listener;

    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setTimeouts(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void flashArduino(String arduinoIp, File binFile) throws Exception {
        Log.d(TAG, "==================================================");
        Log.d(TAG, "=== INIZIO PROCESSO OTA (DEBUG ESTESO) ===");
        Log.d(TAG, "==================================================");
        logMessage("Avvio OTA verso IP: " + arduinoIp + ", Porta: " + port);

        if (binFile == null || !binFile.exists()) {
            String err = "Il file binario non esiste o è null: " + (binFile != null ? binFile.getAbsolutePath() : "null");
            Log.e(TAG, err);
            logMessage("ERRORE: " + err);
            throw new Exception(err);
        }

        long fileSize = binFile.length();
        Log.d(TAG, "File binario validato. Percorso: " + binFile.getAbsolutePath());
        Log.d(TAG, "Dimensione file binario: " + fileSize + " bytes");
        logMessage("Dimensione firmware: " + fileSize + " bytes");

        String urlString = "http://" + arduinoIp + ":" + port + "/sketch";
        Log.d(TAG, "URL di destinazione generato: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            // Configurazione Autenticazione Basic
            String credentials = username + ":" + password;
            String basicAuth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", basicAuth);
            Log.d(TAG, "Autenticazione Basic configurata con utente: " + username);

            // Intestazioni per il flusso di byte grezzi
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", String.valueOf(fileSize));

            // FONDAMENTALE: Forza la dimensione fissa per evitare problemi di chunking sull'ESP32
            conn.setFixedLengthStreamingMode(fileSize);

            Log.d(TAG, "Apertura dello stream di output verso l'hardware...");
            logMessage("Apre la connessione socket con l'ESP32...");

            OutputStream out = conn.getOutputStream();
            FileInputStream fis = new FileInputStream(binFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesWritten = 0;

            Log.d(TAG, "Inizio scrittura pacchetti di rete...");
            long startTime = System.currentTimeMillis();

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesWritten += bytesRead;

                Thread.sleep(100);

                int percent = (int) ((totalBytesWritten * 100) / fileSize);

                // Log dettagliati a frequenza controllata o verbose
                Log.v(TAG, "Progresso invio: " + totalBytesWritten + "/" + fileSize + " bytes (" + percent + "%)");

                if (listener != null) {
                    listener.onProgress(percent, totalBytesWritten, fileSize);
                }

            }

            out.flush();
            fis.close();
            out.close();

            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Invio completato in " + duration + " ms. Totale bytes inviati: " + totalBytesWritten);
            logMessage("Invio dati completato. In attesa di risposta dal bootloader...");

            // Otteniamo il codice di risposta
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Codice di risposta HTTP ricevuto da Arduino: " + responseCode);

            if (responseCode != 200) {
                // Leggiamo eventuali messaggi di errore restituiti dall'ESP32
                String errorDetails = "";
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    errorDetails = sb.toString();
                    reader.close();
                } catch (Exception e) {
                    errorDetails = "Impossibile leggere lo stream di errore dell'ESP32";
                }

                Log.e(TAG, "OTA FALLITO! Codice HTTP: " + responseCode + " - Risposta server: " + errorDetails);
                logMessage("Fallito! HTTP " + responseCode + ": " + errorDetails);
                throw new Exception("Aggiornamento rifiutato. Codice HTTP: " + responseCode + " | Dettagli: " + errorDetails);
            } else {
                Log.d(TAG, "OTA RIUSCITO CON SUCCESSO! L'Arduino applicherà il binario e si riavvierà.");
                logMessage("OTA Riuscito! L'Arduino si sta riavviando...");
            }

        } catch (Exception e) {
            Log.e(TAG, "Eccezione critica durante il blocco OTA: " + e.getMessage(), e);
            logMessage("Eccezione: " + e.getMessage());
            throw e;
        } finally {
            conn.disconnect();
            Log.d(TAG, "Connessione HTTP disconnessa e risorse rilasciate.");
            Log.d(TAG, "==================================================");
        }
    }

    private void logMessage(String msg) {
        if (listener != null) {
            listener.onLog(msg);
        }
    }
}