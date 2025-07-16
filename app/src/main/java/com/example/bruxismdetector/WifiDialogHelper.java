package com.example.bruxismdetector;

import android.app.Activity;


import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.Manifest;
import androidx.annotation.RequiresPermission;

public class WifiDialogHelper {

    public interface WifiPasswordCallback {
        void onPasswordEntered(String ssid, String password);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static void showWifiPasswordDialog(Activity act, WifiPasswordCallback callback) {
        WifiManager wifiManager = (WifiManager) act.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        if (ssid == null || ssid.equals("<unknown ssid>")) {
            ssid = "(SSID not available)";
        }

        TextView ssidLabel = new TextView(act.getApplicationContext());
        ssidLabel.setText("Connected to: " + ssid);
        ssidLabel.setPadding(32, 32, 32, 16);
        ssidLabel.setTextSize(16);

        EditText input = new EditText(act.getApplicationContext());
        input.setHint("Enter Wi-Fi Password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(32, 16, 32, 32);

        LinearLayout layout = new LinearLayout(act.getApplicationContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(ssidLabel);
        layout.addView(input);

        final String finalSsid = ssid;

        act.runOnUiThread(() -> {

            new AlertDialog.Builder(act)
                    .setTitle("Wi-Fi Setup")
                    .setView(layout)
                    .setCancelable(false)
                    .setPositiveButton("Send", (dialog, which) -> {
                        String password = input.getText().toString();
                        if (!password.isEmpty()) {
                            callback.onPasswordEntered(finalSsid, password);
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                    .show();
        });

    }
}
