package com.example.bruxismdetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CameraTest extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_test2);

        if (!arePermissionsGranted()) {
            requestPermissions();
        }

        Button startServiceButton = findViewById(R.id.btn_start_service);
        Button stopServiceButton = findViewById(R.id.btn_stop_service);
        Button triggerStartButton = findViewById(R.id.btn_trigger_start);
        Button triggerStopButton = findViewById(R.id.btn_trigger_stop);

        // Usa i metodi di controllo corretti per il servizio completo
        startServiceButton.setOnClickListener(v -> startVideoService());
        stopServiceButton.setOnClickListener(v -> stopVideoService());
        triggerStartButton.setOnClickListener(v -> sendStartTrigger());
        triggerStopButton.setOnClickListener(v -> sendStopTrigger());
    }

    private boolean arePermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSIONS_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Start service now..", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Need camera access.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void startVideoService() {
        if (!arePermissionsGranted()) {
            Toast.makeText(this, "Grant access to camera", Toast.LENGTH_SHORT).show();
            return;
        }
        String sessionFolderName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());

        Intent intent = new Intent(this, VideoRecordingService.class);
        intent.setAction(VideoRecordingService.ACTION_START_SERVICE);
        intent.putExtra(VideoRecordingService.EXTRA_SESSION_FOLDER_NAME, sessionFolderName);

        startForegroundService(intent);
        Toast.makeText(this, "Service started.", Toast.LENGTH_SHORT).show();
    }

    // Metodo per fermare completamente il servizio
    public void stopVideoService() {
        Intent intent = new Intent(this, VideoRecordingService.class);
        intent.setAction(VideoRecordingService.ACTION_STOP_SERVICE);
        startService(intent);
        Toast.makeText(this, "Service stopped.", Toast.LENGTH_SHORT).show();
    }

    // Metodo per inviare il trigger di inizio salvataggio
    public void sendStartTrigger() {
        Intent intent = new Intent(this, VideoRecordingService.class);
        intent.setAction(VideoRecordingService.ACTION_TRIGGER_START_SAVE);
        startService(intent);
        Toast.makeText(this, "Trigger START sent.", Toast.LENGTH_SHORT).show();
    }

    // Metodo per inviare il trigger di fine salvataggio
    public void sendStopTrigger() {
        Intent intent = new Intent(this, VideoRecordingService.class);
        intent.setAction(VideoRecordingService.ACTION_TRIGGER_STOP_SAVE);
        startService(intent);
        Toast.makeText(this, "Trigger STOP sent.", Toast.LENGTH_SHORT).show();
    }
}