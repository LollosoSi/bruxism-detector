package com.example.bruxismdetector;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PermissionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permissions);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainvert), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        checkset();
    }

    public void checkset(){
        int uncoolperms = 0;
        if(hasExternalPerm()){

            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.ext_storage));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.ext_storage)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    setRequestWriteExternalStorage(null);
                    checkset();
                }
            });
        }

        if(hasStorage(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.Storage));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.Storage)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestStoragePermission(null);
                    checkset();
                }
            });
        }

        if(hasFloatingPermission(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.floatview));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.floatview)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    checkFloatingPermission(null);
                    checkset();
                }
            });
        }

        if(isExactAlarmPermissionGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.alms));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.alms)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestExactAlarmPermission(PermissionsActivity.this);
                    checkset();
                }
            });
        }

        if(isExactAlarmPermissionGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.alms));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.alms)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestExactAlarmPermission(PermissionsActivity.this);
                    checkset();
                }
            });
        }

        if(hasNotificationPermission(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.nts));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.nts)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    askNotificationPerm();
                    checkset();
                }
            });
        }

        if(isMicrophonePermissionGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.microphonep));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.microphonep)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestMicrophonePermission(PermissionsActivity.this, 1000);
                    checkset();
                }
            });
        }

        if(isCameraPermissionGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.camerap));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.camerap)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestCameraPermission(PermissionsActivity.this, 10000);
                    checkset();
                }
            });
        }

        if(isForegroundLocationGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.foreloc));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.foreloc)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestForegroundLocationPermission(PermissionsActivity.this, 10000);
                    checkset();
                }
            });
        }

        if(isBackgroundLocationGranted(this)){
            com.google.android.material.materialswitch.MaterialSwitch sw = ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.backloc));
            sw.setChecked(true);
            sw.setEnabled(false);
        }else{
            uncoolperms++;
            ((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.backloc)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    requestBackgroundLocationPermission(PermissionsActivity.this, 10000);
                    checkset();
                }
            });
        }


        if(uncoolperms==0){
            // restart the application
            //Intent intent = new Intent(this, MainActivity.class);
            //startActivity(intent);
            finish();
        }
    }


    public static boolean isForegroundLocationGranted(Context context) {
        return (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) || isBackgroundLocationGranted(context);
    }

    public static boolean isBackgroundLocationGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // No need for background location before Android 10
    }


    public static void requestForegroundLocationPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                requestCode);
    }

    public static void requestBackgroundLocationPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    requestCode);
        }
    }


    public static boolean isCameraPermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCameraPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, requestCode);
    }

    public static boolean isMicrophonePermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestMicrophonePermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, requestCode);
    }


    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // For older versions, permission is automatically granted
    }

    public static void requestExactAlarmPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }

    public static boolean isExactAlarmPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Permission not required on older Android versions
    }


    public static boolean hasAllPermissions(Context ct){
        return hasExternalPerm() && hasStorage(ct)
                && hasFloatingPermission(ct) && isExactAlarmPermissionGranted(ct)
                && hasNotificationPermission(ct) && isCameraPermissionGranted(ct)
                && isMicrophonePermissionGranted(ct)
                && isBackgroundLocationGranted(ct) && isForegroundLocationGranted(ct);
    }

    public void askNotificationPerm(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    1001
            );
        }
    }

    public static boolean hasExternalPerm(){
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return Environment.isExternalStorageManager();
         return true;
    }
    public void setRequestWriteExternalStorage(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;

public static boolean hasStorage(Context ct){
    return ContextCompat.checkSelfPermission(ct, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED;
}
    public void requestStoragePermission(View v) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        checkset();
    }



    public static boolean hasFloatingPermission(Context ct){
        return Settings.canDrawOverlays(ct);
    }


    //Helper method for checking overlay floating permission
    public void checkFloatingPermission(View v) {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityFloatingPermission.launch(intent);//this will open device settings for over lay permission window

        }
    }

    //Initialize ActivityResultLauncher. Note here that no need custom request code
    ActivityResultLauncher<Intent> startActivityFloatingPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    checkset();
                }
            });
}