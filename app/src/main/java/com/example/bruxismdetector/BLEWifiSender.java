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
import android.preference.PreferenceManager;
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
                Log.i(TAG, "Credentials sent successfully.");

                // Now, attempt to read the IP address characteristic
                BluetoothGattService service = gatt.getService(SERVICE_UUID); // Assuming IP char is in the same service
                if (service != null) {
                    BluetoothGattCharacteristic ipCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (ipCharacteristic != null) {
                        if ((ipCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            Log.d(TAG, "Attempting to read IP characteristic: " + ipCharacteristic.getUuid());
                            if (!gatt.readCharacteristic(ipCharacteristic)) {
                                Log.e(TAG, "Failed to initiate read for IP characteristic.");
                                stop(); // Stop if read initiation fails
                            }
                            // If readCharacteristic returns true, onCharacteristicRead will be called later.
                        } else {
                            Log.e(TAG, "IP characteristic " + CHARACTERISTIC_UUID + " does not have READ property.");
                            stop();
                        }
                    } else {
                        Log.w(TAG, "IP characteristic " + CHARACTERISTIC_UUID + " not found.");
                        stop(); // Stop if the IP characteristic isn't found
                    }
                } else {
                    Log.w(TAG, "Service " + SERVICE_UUID + " not found for reading IP characteristic.");
                    stop(); // Stop if service isn't found
                }
            } else {
                Log.e(TAG, "Credential write failed with status: " + status);
                stop(); // Stop if write failed
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            // This is the old callback signature.
            // For API 33+, you'd also implement the one without byte[] value and call a common handler.
            // For simplicity here, we'll use this one. Ensure your target/compile SDK handles it.

            // super.onCharacteristicRead(gatt, characteristic, value, status); // Not strictly needed unless you have a superclass doing something

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) { // Check if it's the IP characteristic
                    if (value.length > 0) {
                        String receivedData = new String(value, StandardCharsets.UTF_8).trim();
                        Log.i(TAG, "Successfully read IP Address: \"" + receivedData + "\"");
                        // TODO: Process the receivedData (e.g., parse IP, update UI, etc.)
                        // Now you have the IP. You can decide what to do next.
                        // Perhaps now you want to stop, or keep the connection for other things.
                        // For this example, let's assume you stop after successfully reading the IP.
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                        prefs.edit().putString("tcp_address", receivedData).apply();

                        blecallback.onIPReceived(receivedData);

                        Log.d(TAG, "IP Address received. Stopping BLE operations.");
                        stop(); // Call stop AFTER successfully processing the IP

                    } else {
                        Log.w(TAG, "IP Characteristic " + characteristic.getUuid() + " read with null or empty value.");
                        stop(); // Stop if read was for IP char but value is empty
                    }
                } else {
                    Log.d(TAG, "Read from other characteristic: " + characteristic.getUuid());
                    // If you read other characteristics, decide if you stop or not.
                    // For now, let's assume any other read also leads to a stop for simplicity.
                    stop();
                }
            } else {
                Log.w(TAG, "Characteristic read failed for " + characteristic.getUuid() + " with status: " + status);
                stop(); // Stop if any read fails
            }
        }
    };
}
