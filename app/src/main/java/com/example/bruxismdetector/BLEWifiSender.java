package com.example.bruxismdetector;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelUuid;
import androidx.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BLEWifiSender {

    private static final String TAG = "BLEWifiSender";

    private static final String TARGET_NAME = "BruxismDetector";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("abcdefab-1234-5678-1234-56789abcdef0");

    private Activity activity;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;

    private BluetoothGatt currentGatt;
    private boolean sent = false;

    public interface BLECallback{
        void onIPReceived(String ip);
    };

    BLECallback blecallback;
    public BLEWifiSender(Activity act, BLECallback blc) {
        this.activity = act;
        blecallback = blc;
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = manager.getAdapter();
        this.scanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanFilter filter = new ScanFilter.Builder().setDeviceName(TARGET_NAME).build();
        ScanSettings settings = new ScanSettings.Builder().build();
startScan();
Log.d(TAG, "Started BLE scan for " + TARGET_NAME);

    }

    public void stop() {
        stopScan();

        if (currentGatt != null) {
            currentGatt.disconnect();
            currentGatt.close();
            currentGatt = null;
        }

        Log.d(TAG, "Stopped BLE scanning and disconnected");
    }

    BluetoothDevice device = null;


    String ssid, password;
    private boolean scanning = false;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            // Get device name from scan record or device object
            String advName = null;
            if (result.getScanRecord() != null) {
                advName = result.getScanRecord().getDeviceName();
            }
            String name = advName != null ? advName : device.getName();

            if (TARGET_NAME.equals(name)) {
                Log.d(TAG, "Found target BLE device: " + device.getAddress());

                // Stop scanning once device found (optional)
                stopScan();

                // Prompt for WiFi credentials dialog, then connect
                WifiDialogHelper.WifiPasswordCallback wpc = new WifiDialogHelper.WifiPasswordCallback() {
                    @Override
                    public void onPasswordEntered(String wssid, String wpassword) {
                        ssid = wssid;
                        password = wpassword;
                        connectToDevice(device);
                    }
                };

                WifiDialogHelper.showWifiPasswordDialog(activity, wpc);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code " + errorCode);
        }
    };

    public void startScan() {
        if (scanning) return;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is disabled or not supported.");
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "Failed to get BLE scanner.");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Real-time scan
                .build();

        scanner.startScan(null, settings, scanCallback);
        scanning = true;

        Log.d(TAG, "BLE scanning started.");
    }

    public void stopScan() {
        if (!scanning || scanner == null) return;

        scanner.stopScan(scanCallback);
        scanning = false;
        Log.d(TAG, "BLE scanning stopped.");
    }


    private void connectToDevice(BluetoothDevice device) {
        currentGatt = device.connectGatt(activity, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server. Discovering services...");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");
                stop();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic != null) {
                    String combined = ssid + "\"" + password + "\0";
                    characteristic.setValue(combined.getBytes());
                    boolean success = gatt.writeCharacteristic(characteristic);
                    Log.d(TAG, "Writing credentials: " + success);
                } else {
                    Log.w(TAG, "Characteristic not found");
                    stop();
                }
            } else {
                Log.w(TAG, "Service not found");
                stop();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "Write complete with status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sent = true;
                Log.i(TAG, "Credenziali inviate! In attesa che Arduino si connetta al WiFi...");

                // Attende 1 secondo prima del primo tentativo di lettura dell'IP
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (currentGatt != null) {
                        gatt.readCharacteristic(characteristic);
                    }
                }, 1000);
            } else {
                Log.e(TAG, "Scrittura credenziali fallita con codice: " + status);
                stop();
            }
        }

        private void handleReceivedData(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (value == null || value.length == 0) return;

            String receivedData = new String(value, StandardCharsets.UTF_8).trim();
            Log.d(TAG, "Dato letto da BLE: \"" + receivedData + "\"");

            // Verifica se il dato letto è un indirizzo IP valido (es. 192.168.1.50)
            if (receivedData.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                Log.i(TAG, "IP valido ricevuto con successo: " + receivedData);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                prefs.edit().putString("tcp_address", receivedData).apply();

                activity.runOnUiThread(() -> blecallback.onIPReceived(receivedData));

                // Chiude il BLE solo dopo aver ottenuto il vero IP
                stop();
            } else {
                // Arduino sta ancora negoziando la connessione con il router, riprova tra 600ms
                Log.d(TAG, "Arduino non ha ancora l'IP. Nuovo tentativo tra 600ms...");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (currentGatt != null) {
                        gatt.readCharacteristic(characteristic);
                    }
                }, 600);
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleReceivedData(gatt, characteristic, value);
            } else {
                Log.w(TAG, "Lettura fallita con codice: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleReceivedData(gatt, characteristic, characteristic.getValue());
            }
        }
    };
}
