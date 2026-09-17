package com.example.bruxismdetector.cloud;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

public class NetworkClient {
    private static final String BASE_URL = "https://www.roccaccino.it/bruxism-detector/";

    // Usato per ping.php, list_files.php e download.php
    public static HttpURLConnection postForm(String endpoint, String urlParameters) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(urlParameters.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    // Usato per upload_3.php per inviare file con limitazione a 50MB e controlli RCE[cite: 9]
    public static HttpURLConnection postMultipart(String endpoint, String uuid, String pwd, String relativePath, File file) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)) {

            // Aggiungi campi testo
            addFormField(writer, boundary, "uuid", uuid);
            addFormField(writer, boundary, "password", pwd);
            addFormField(writer, boundary, "path_relative", relativePath);

            // Aggiungi file
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: application/octet-stream\r\n\r\n").flush();

            try (FileInputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
            writer.append("\r\n").append("--").append(boundary).append("--\r\n").flush();
        }
        return conn;
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        if (value == null) return;
        writer.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n").flush();
    }

    public static HttpURLConnection postDonationBatch(String endpoint, String uuid, File[] files) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false); // Impedisce i bug del redirect
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)) {

            // Invia solo l'UUID
            addFormField(writer, boundary, "uuid", uuid);

            // Invia tutti i file nell'array
            for (File file : files) {
                if (file == null || !file.exists()) continue;

                writer.append("--").append(boundary).append("\r\n");
                // IMPORTANTE: name="files[]" permette al PHP di leggere l'array
                writer.append("Content-Disposition: form-data; name=\"files[]\"; filename=\"").append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: application/octet-stream\r\n\r\n").flush();

                try (FileInputStream inputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }
                writer.append("\r\n").flush();
            }
            writer.append("--").append(boundary).append("--\r\n").flush();
        }
        return conn;
    }


    public static HttpURLConnection postDeleteFiles(String endpoint, String uuid, String pwd, String filesCsv) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String urlParameters = "uuid=" + URLEncoder.encode(uuid, "UTF-8") +
                "&password=" + URLEncoder.encode(pwd, "UTF-8") +
                "&files=" + URLEncoder.encode(filesCsv, "UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(urlParameters.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    // Recupera il testo e la versione dal server
    public static JSONObject fetchCurrentAgreement() throws Exception {
        HttpURLConnection conn = postForm("agreement.php", "action=get");
        if (conn.getResponseCode() == 200) {
            try (InputStream in = conn.getInputStream(); java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\A")) {
                return new JSONObject(scanner.hasNext() ? scanner.next() : "{}");
            }
        }
        throw new Exception("HTTP " + conn.getResponseCode());
    }

    // Invia l'accettazione dopo il login
    public static boolean postAgreementAcceptance(String uuid, String pwd, int version) {
        try {
            String safeUuid = URLEncoder.encode(uuid, "UTF-8");
            String safePwd = URLEncoder.encode(pwd, "UTF-8");
            HttpURLConnection conn = postForm("agreement.php",
                    "action=accept&uuid=" + safeUuid + "&password=" + safePwd + "&version=" + version);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static HttpURLConnection postDeleteAccount(String endpoint, String uuid, String pwd) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String urlParameters = "uuid=" + URLEncoder.encode(uuid, "UTF-8") +
                "&password=" + URLEncoder.encode(pwd, "UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(urlParameters.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }
}