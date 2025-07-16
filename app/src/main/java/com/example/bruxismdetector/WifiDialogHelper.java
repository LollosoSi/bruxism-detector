package com.example.bruxismdetector;

import android.app.Activity;


import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.Manifest;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WifiDialogHelper {

    public interface WifiPasswordCallback {
        void onPasswordEntered(String ssid, String password);
    }

    private static final String TAG = "WifiUtils";


    /**
     * Attempts to get the current Wi-Fi SSID or the Hotspot SSID (on pre-Oreo devices).
     *
     * IMPORTANT LIMITATIONS:
     * 1. Getting connected Wi-Fi SSID requires ACCESS_FINE_LOCATION permission (or ACCESS_COARSE_LOCATION
     *    on some older versions, but FINE is generally needed for accuracy and newer APIs).
     * 2. Getting Hotspot SSID is ONLY attempted on Android versions older than 8.0 (Oreo)
     *    using reflection, which is UNRELIABLE and NOT OFFICIALLY SUPPORTED.
     * 3. On Android 8.0 (Oreo) and newer, this method CANNOT retrieve the user-configured
     *    Hotspot SSID for regular apps.
     *
     * @param act The current Activity context.
     * @return The SSID string if found, otherwise null or an empty string.
     *         Returns "<unknown ssid>" if connected but SSID cannot be determined.
     */
    public static String[] getCurrentSSID(Activity act) {
        if (act == null) {
            Log.e(TAG, "Activity context is null.");
            return new String[]{"",""};
        }

        WifiManager wifiManager = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WifiManager is null.");
            return new String[]{"",""};
        }

        // --- Try to get Hotspot SSID first (ONLY for pre-Oreo and if enabled) ---
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                Method isWifiApEnabledMethod = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                isWifiApEnabledMethod.setAccessible(true);
                boolean isApEnabled = (Boolean) isWifiApEnabledMethod.invoke(wifiManager);

                if (isApEnabled) {
                    Method getWifiApConfigurationMethod = wifiManager.getClass().getDeclaredMethod("getWifiApConfiguration");
                    getWifiApConfigurationMethod.setAccessible(true);
                    WifiConfiguration wifiApConfiguration = (WifiConfiguration) getWifiApConfigurationMethod.invoke(wifiManager);
                    if (wifiApConfiguration != null && wifiApConfiguration.SSID != null) {
                        Log.i(TAG, "Hotspot SSID (pre-Oreo, via reflection): " + wifiApConfiguration.SSID);
                        return new String[]{wifiApConfiguration.SSID, wifiApConfiguration.BSSID}; // Return hotspot SSID
                    } else {
                        Log.i(TAG, "Hotspot enabled (pre-Oreo), but SSID not retrieved via reflection.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get hotspot SSID via reflection (pre-Oreo): " + e.getMessage());
            }
        } else {
            // On Android 8.0+ and if hotspot is enabled, we still can't get its SSID programmatically
            // for a regular app. We could check if it's enabled, but can't get the name.
            // For simplicity of this method's return, we'll fall through to Wi-Fi check.
            // You could add a specific log here if needed:
            // Log.i(TAG, "Hotspot check on Android 8.0+: Cannot get SSID for regular apps.");
        }

        // --- If Hotspot SSID wasn't found (or on Oreo+), try connected Wi-Fi SSID ---
        // Requires ACCESS_FINE_LOCATION (or COARSE on older, but FINE is better)
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ConnectivityManager connManager = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager == null) {
                Log.e(TAG, "ConnectivityManager is null.");
                return new String[]{"",""};
            }

            Network activeNetwork = connManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities networkCapabilities = connManager.getNetworkCapabilities(activeNetwork);
                if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    // Device is connected to a Wi-Fi network
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        String retrievedSsid = wifiInfo.getSSID();
                        if (retrievedSsid != null && !retrievedSsid.isEmpty()) {
                            if (retrievedSsid.equalsIgnoreCase("<unknown ssid>")) {
                                Log.w(TAG, "Connected to Wi-Fi, but SSID is <unknown ssid>.");
                                return new String[]{"",""}; // Explicitly return this if it's the case
                            }
                            // Remove surrounding quotes if present
                            if (retrievedSsid.startsWith("\"") && retrievedSsid.endsWith("\"")) {
                                retrievedSsid = retrievedSsid.substring(1, retrievedSsid.length() - 1);
                            }
                            Log.i(TAG, "Connected Wi-Fi SSID: " + retrievedSsid);
                            return new String[]{retrievedSsid,""};
                        } else {
                            Log.w(TAG, "Connected to Wi-Fi, but SSID is null or empty from WifiInfo.");
                        }
                    } else {
                        Log.w(TAG, "Connected to Wi-Fi, but WifiInfo is null.");
                    }
                }
            }
        } else {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted. Cannot get connected Wi-Fi SSID.");
            // Consider how you want to handle this: return null, or a specific string indicating permission issue.
            return new String[]{"",""}; // Or some indicator like "PERMISSION_NEEDED"
        }

        Log.i(TAG, "No usable SSID found (not connected to Wi-Fi, or hotspot SSID not available/retrievable).");
        return new String[]{"",""}; // Or return "" if you prefer an empty string for "not found"
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static void showWifiPasswordDialog(Activity act, WifiPasswordCallback callback) {
        String[] ssinfo = getCurrentSSID(act);
        String ssid = ssinfo[0];
        String bssid = ssinfo[1];

        EditText ssidText = new EditText(act.getApplicationContext());
        ssidText.setHint("Enter Wi-Fi SSID");
        ssidText.setText(ssid);
        ssidText.setPadding(32, 32, 32, 32);
        ssidText.setTextSize(16);

        EditText input = new EditText(act.getApplicationContext());
        input.setHint("Enter Wi-Fi Password");
        input.setText(bssid);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(32, 16, 32, 32);

        LinearLayout layout = new LinearLayout(act.getApplicationContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(ssidText);
        layout.addView(input);

        final String finalSsid = ssidText.getText().toString();

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
