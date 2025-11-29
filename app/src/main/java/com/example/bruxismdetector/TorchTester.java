package com.example.bruxismdetector;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

public class TorchTester {

    private static final String TAG = "TorchManager";
    private final CameraManager cameraManager;
    private String cameraId;
    private int maxStrengthLevel = 1;

    public TorchTester(@NonNull Context context) {
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        initCameraId();
    }

    private void initCameraId() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                if (hasFlash != null && hasFlash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    this.cameraId = id;

                    // Recupera livello massimo (API 33+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Integer max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                        if (max != null) {
                            maxStrengthLevel = max;
                        }
                    }
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Errore inizializzazione camera", e);
        }
    }

    public void setTorch(boolean enable, int percentage) {
        if (cameraId == null) return;

        try {
            if (!enable) {
                cameraManager.setTorchMode(cameraId, false);
                return;
            }

            // API 33+ (Android 13) per controllo intensità
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrengthLevel > 1) {
                int p = Math.max(0, Math.min(percentage, 100));
                int strength = 1 + (p * (maxStrengthLevel - 1) / 100);
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, strength);
            } else {
                // Fallback standard on/off
                cameraManager.setTorchMode(cameraId, true);
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, "Errore impostazione torcia", e);
        }
    }

    public void close() {
        // Spegne la torcia alla chiusura
        setTorch(false, 0);
    }
}