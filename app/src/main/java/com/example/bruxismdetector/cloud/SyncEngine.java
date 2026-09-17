package com.example.bruxismdetector.cloud;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

public class SyncEngine {
    private final CloudPreferences prefs;
    private final File recordingsRoot;
    private final Context context;

    public SyncEngine(Context context) {
        this.context = context;
        this.prefs = new CloudPreferences(context);

        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        this.recordingsRoot = new File(documentsDir, "RECORDINGS");
    }

    // Metodo per inviare l'errore alla UI
    private void broadcastError(String message) {
        Intent intent = new Intent("com.example.bruxismdetector.SYNC_ERROR");
        intent.putExtra("error_msg", message);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private java.util.HashMap<String, RemoteFileMeta> syncFromServerToDevice(String uuid, String pwd, boolean performDownload) {
        java.util.HashMap<String, RemoteFileMeta> remoteFiles = new java.util.HashMap<>();
        try {
            String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");
            String safeUuid = java.net.URLEncoder.encode(cleanUuid, "UTF-8");
            String safePwd = java.net.URLEncoder.encode(pwd, "UTF-8");

            HttpURLConnection conn = NetworkClient.postForm("list_files.php", "uuid=" + safeUuid + "&password=" + safePwd);
            int code = conn.getResponseCode();

            if (code == 200) {
                String response = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A").next().trim();

                if (response.startsWith("[")) {
                    JSONArray serverFiles = new JSONArray(response);

                    for (int i = 0; i < serverFiles.length(); i++) {
                        JSONObject node = serverFiles.getJSONObject(i);
                        String relPath = node.getString("path");
                        long remoteTimestamp = node.getLong("timestamp");
                        long remoteSize = node.has("size") ? node.getLong("size") : -1;

                        remoteFiles.put(relPath, new RemoteFileMeta(remoteTimestamp, remoteSize));

                        File localFile = new File(recordingsRoot, relPath);
                        boolean shouldDownload = false;

                        if (!localFile.exists()) {
                            shouldDownload = true;
                        } else {
                            android.util.Log.d("SyncEngine", "File exists locally, skipping download: " + relPath);
                        }

                        if (shouldDownload && performDownload) {
                            downloadSingleFile(safeUuid, safePwd, relPath, localFile);
                        }
                    }
                } else {
                    Log.e("SyncEngine", "list_files.php invalid response: " + response);
                    broadcastError("Sync failed: Invalid server response");
                }
            } else {
                InputStream errStream = conn.getErrorStream();
                String errText = errStream != null ? new Scanner(errStream).useDelimiter("\\A").next().trim() : "";
                Log.e("SyncEngine", "list_files.php error HTTP " + code + ": " + errText);
                broadcastError("Network error during file list: HTTP " + code);
            }
        } catch (Exception e) {
            Log.e("SyncEngine", "Exception in syncFromServerToDevice", e);
            broadcastError("Sync exception: " + e.getMessage());
        }
        return remoteFiles;
    }

    public void executeSync(boolean performDownload) {
        String uuid = prefs.getUUID();
        if (uuid == null) {
            Log.w("SyncEngine", "Cannot execute sync: UUID is null");
            return;
        }
        String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");

        pingCloud(performDownload ? "SYNC_FULL" : "SYNC_UPLOAD");

        String pwd = prefs.getPassword();
        if (pwd == null) {
            Log.w("SyncEngine", "Cannot execute sync: Password is null");
            return;
        }

        java.util.HashMap<String, RemoteFileMeta> remoteFilesMap = syncFromServerToDevice(uuid, pwd, performDownload);
        syncFromDeviceToServer(uuid, pwd, remoteFilesMap);
    }

    private void downloadSingleFile(String uuid, String pwd, String relPath, File dest) {
        try {
            dest.getParentFile().mkdirs();
            HttpURLConnection conn = NetworkClient.postForm("download.php", "uuid=" + uuid + "&password=" + pwd + "&path_relative=" + relPath);
            int code = conn.getResponseCode();

            if (code == 200) {
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                Log.d("SyncEngine", "Successfully downloaded: " + relPath);
            } else {
                InputStream errStream = conn.getErrorStream();
                String errText = errStream != null ? new Scanner(errStream).useDelimiter("\\A").next().trim() : "";
                Log.e("SyncEngine", "Download failed for " + relPath + " - HTTP " + code + ": " + errText);
            }
        } catch (Exception e) {
            Log.e("SyncEngine", "Exception downloading file: " + relPath, e);
        }
    }

    public void uploadTrainingDonation() {
        String uuid = prefs.getUUID();
        if (uuid == null) return;
        String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");

        File trainingDir = new File(recordingsRoot, "TrainingData");
        File clenchingFile = new File(trainingDir, "clenching.csv");
        File nonClenchingFile = new File(trainingDir, "non_clenching.csv");

        if (!clenchingFile.exists() && !nonClenchingFile.exists()) {
            android.util.Log.d("SyncEngine", "Local donation files not found.");
            return;
        }

        try {
            File[] filesToDonate = new File[]{clenchingFile, nonClenchingFile};
            HttpURLConnection conn = NetworkClient.postDonationBatch("donate_training.php", cleanUuid, filesToDonate);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = "";
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                if (scanner.hasNext()) {
                    responseText = scanner.next().trim();
                }
            }

            if (code == 200) {
                android.util.Log.d("SyncEngine", "Donation completed successfully! Response: " + responseText);
            } else {
                android.util.Log.e("SyncEngine", "Error while donating: HTTP " + code + ": " + responseText);
            }
        } catch (Exception e) {
            android.util.Log.e("SyncEngine", "Exception occurred during file donation", e);
        }
    }

    private void syncFromDeviceToServer(String uuid, String pwd, java.util.HashMap<String, RemoteFileMeta> remoteFilesMap) {
        if (!prefs.isOptInGeneral()) return;
        uploadDirectory(recordingsRoot, uuid, pwd, remoteFilesMap, true);
    }

    private void uploadDirectory(File dir, String uuid, String pwd, java.util.HashMap<String, RemoteFileMeta> remoteFilesMap, boolean filter_directories) {
        String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");
        File[] files = dir.listFiles();
        if (files == null) return;

        String[] accepted_dir_names = {"Sleep", "ACCEL", "NOISE", "RAW", "TrainingData"};

        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();

                if (filter_directories && java.util.Arrays.stream(accepted_dir_names).noneMatch(dirName::equals))
                    continue;

                if (dirName.equals("Sleep") && !prefs.isOptInSleep()) continue;
                if ((dirName.equals("ACCEL") || dirName.equals("NOISE")) && !prefs.isOptInSensors())
                    continue;
                if (dirName.equals("RAW") && !prefs.isOptInRaw()) continue;
                if (dirName.equals("TrainingData") && !prefs.isOptInTraining()) continue;

                uploadDirectory(file, cleanUuid, pwd, remoteFilesMap, false);
            } else {
                if (file.getName().equalsIgnoreCase("beeps.db") && !prefs.isOptInBeep()) continue;
                if (!(file.getName().endsWith(".csv") || file.getName().endsWith(".db"))) continue;

                if (file.exists()) {
                    uploadSingleFile(file, cleanUuid, pwd, remoteFilesMap);
                } else {
                    Log.d("SyncEngine", "File " + file.getName() + " no longer exists, skipping upload");
                }
            }
        }
    }

    private void uploadSingleFile(File file, String uuid, String pwd, java.util.HashMap<String, RemoteFileMeta> remoteFilesMap) {
        String relativePath = file.getAbsolutePath()
                .replace(recordingsRoot.getAbsolutePath() + "/", "")
                .replace("\\", "/");

        if (remoteFilesMap != null && remoteFilesMap.containsKey(relativePath)) {
            RemoteFileMeta meta = remoteFilesMap.get(relativePath);
            long localTimestamp = file.lastModified() / 1000;
            long localSize = file.length();

            if (localSize == meta.size && localTimestamp <= meta.timestamp) {
                android.util.Log.d("SyncEngine", "Skipping upload (up to date): " + relativePath);
                return;
            }
        }

        android.util.Log.d("SyncEngine", "Uploading: " + relativePath);

        try {
            String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");
            HttpURLConnection conn = NetworkClient.postMultipart("upload.php", cleanUuid, pwd, relativePath, file);
            int code = conn.getResponseCode();

            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = is != null ? new java.util.Scanner(is).useDelimiter("\\A").next().trim() : "";

            if (code != 200) {
                broadcastError("Error uploading " + file.getName() + ": HTTP " + code);
                android.util.Log.e("SyncEngine", "SERVER REJECTED " + file.getName() + " - Code: " + code + " Response: " + responseText);
            }
        } catch (Exception e) {
            android.util.Log.e("SyncEngine", "Upload exception for " + file.getName(), e);
            broadcastError("Upload exception: " + e.getMessage());
        }
    }

    public boolean deleteRemoteFiles(java.util.List<String> relativePaths) {
        String uuid = prefs.getUUID();
        String pwd = prefs.getPassword();

        if (uuid == null || pwd == null || relativePaths == null || relativePaths.isEmpty()) {
            return false;
        }

        try {
            String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");

            StringBuilder csvBuilder = new StringBuilder();
            for (int i = 0; i < relativePaths.size(); i++) {
                csvBuilder.append(relativePaths.get(i));
                if (i < relativePaths.size() - 1) {
                    csvBuilder.append(",");
                }
            }

            HttpURLConnection conn = NetworkClient.postDeleteFiles("delete.php", cleanUuid, pwd, csvBuilder.toString());
            int code = conn.getResponseCode();

            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = "";
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                if (scanner.hasNext()) {
                    responseText = scanner.next().trim();
                }
            }

            if (code == 200) {
                android.util.Log.d("SyncEngine", "Cloud delete success: " + responseText);
                return true;
            } else {
                android.util.Log.e("SyncEngine", "Cloud delete error HTTP " + code + ": " + responseText);
                return false;
            }
        } catch (Exception e) {
            android.util.Log.e("SyncEngine", "Exception during cloud delete", e);
            return false;
        }
    }

    public void pingCloud(String action) {
        String uuid = prefs.getUUID();
        if (uuid == null) return;
        String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");

        SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(context);

        org.json.JSONObject prefsJson = new org.json.JSONObject();
        try {
            exportPrefsToJson(prefsJson, sprefs);

            prefsJson.put("opt_in_general", prefs.isOptInGeneral());
            prefsJson.put("opt_in_sensors", prefs.isOptInSensors());
            prefsJson.put("opt_in_raw", prefs.isOptInRaw());
            prefsJson.put("opt_in_training", prefs.isOptInTraining());
            prefsJson.put("opt_in_beep", prefs.isOptInBeep());
            prefsJson.put("opt_in_sleep", prefs.isOptInSleep());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread t = new Thread(() -> {
            try {
                String urlParameters = "uuid=" + cleanUuid +
                        "&date=" + System.currentTimeMillis() +
                        "&action=" + java.net.URLEncoder.encode(action, "UTF-8") +
                "&prefs=" + java.net.URLEncoder.encode(prefsJson.toString(), "UTF-8");

                HttpURLConnection conn = NetworkClient.postForm("ping.php", urlParameters);
                int code = conn.getResponseCode();

                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseText = "";
                if (is != null) {
                    java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                    if (scanner.hasNext()) {
                        responseText = scanner.next().trim();
                    }
                }

                if (code == 200) {
                    android.util.Log.d("SyncEngine", "Ping OK (" + action + "): " + responseText);
                } else {
                    android.util.Log.e("SyncEngine", "Errore Ping HTTP " + code + ": " + responseText);
                }
            } catch (Exception e) {
                android.util.Log.e("SyncEngine", "Error sending ping", e);
            }
        });
        t.start();
    }

    private void exportPrefsToJson(org.json.JSONObject prefsJson, SharedPreferences sprefs) throws org.json.JSONException, PackageManager.NameNotFoundException {
        prefsJson.put("alarm_on_device", sprefs.getBoolean("alarm_on_device", true));
        prefsJson.put("arduino_beep", sprefs.getBoolean("arduino_beep", true));
        prefsJson.put("do_not_alarm", sprefs.getBoolean("do_not_alarm", false));
        prefsJson.put("do_not_beep", sprefs.getBoolean("do_not_beep", false));
        prefsJson.put("noisy_alarm", sprefs.getBoolean("noisy_alarm", false));

        prefsJson.put("classification_threshold", sprefs.getInt("classification_threshold", 0));
        prefsJson.put("use_threshold", sprefs.getBoolean("use_threshold", false));

        prefsJson.put("record_accel", sprefs.getBoolean("record_accel", false));
        prefsJson.put("record_camera", sprefs.getBoolean("record_camera", false));
        prefsJson.put("record_camera_flash", sprefs.getBoolean("record_camera_flash", true));
        prefsJson.put("record_camera_onlyalarms", sprefs.getBoolean("record_camera_onlyalarms", true));
        prefsJson.put("record_noise", sprefs.getBoolean("record_noise", false));

        prefsJson.put("schedule_listener_after_tracker_ends", sprefs.getBoolean("schedule_listener_after_tracker_ends", true));
        prefsJson.put("ServiceHour", sprefs.getInt("ServiceHour", 21));
        prefsJson.put("ServiceMinute", sprefs.getInt("ServiceMinute", 0));
        prefsJson.put("start_trainer_after_tracker_ends", sprefs.getBoolean("start_trainer_after_tracker_ends", false));

        prefsJson.put("editor_tutorial", sprefs.getBoolean("editor_tutorial", false));
        prefsJson.put("redirect_cloud", sprefs.getBoolean("redirect_cloud", true));
        prefsJson.put("regen_graph_scroll", sprefs.getBoolean("regen_graph_scroll", true));
        prefsJson.put("session_collapsed", sprefs.getBoolean("session_collapsed", false));
        prefsJson.put("show_advice", sprefs.getBoolean("show_advice", true));
        prefsJson.put("tutorial", sprefs.getBoolean("tutorial", true));
        prefsJson.put("tutorial_version", sprefs.getInt("tutorial_version", 0));

        prefsJson.put("use_tcp", sprefs.getBoolean("use_tcp", false));

        android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        int currentVersionCode = pInfo.versionCode;
        prefsJson.put("app_version_code", currentVersionCode);
        prefsJson.put("lastversionarduino", sprefs.getInt("lastversionarduino", 0));
    }

    private static class RemoteFileMeta {
        final long timestamp;
        final long size;

        RemoteFileMeta(long timestamp, long size) {
            this.timestamp = timestamp;
            this.size = size;
        }
    }
}