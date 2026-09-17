package com.example.bruxismdetector;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bruxismdetector.cloud.CloudPreferences;
import com.example.bruxismdetector.cloud.NetworkClient;
import com.example.bruxismdetector.cloud.SyncEngine;
import com.example.bruxismdetector.cloud.SyncService;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

public class DataSharingActivity extends AppCompatActivity {

    private CloudPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;

    private SwitchCompat switchMaster, switchSleep, switchSensors, switchRaw, switchTraining, switchBeeps;
    private LinearLayout layoutSubSwitches, mainLayout;
    private Button btnSyncNow, btnSyncPc, btnUpdatePassword, btnDeleteData;
    private TextView tvHelp;

    // Variabili per l'accordo dinamico
    private int serverAgreementVersion = 0;
    private String serverAgreementText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data_sharing);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = new CloudPreferences(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupListeners();

        // Disabilita tutto in attesa del server
        lockUi("");
        fetchAgreementAndCheckState();
    }

    private void initViews() {
        mainLayout = findViewById(R.id.mainLayout);
        layoutSubSwitches = findViewById(R.id.layoutSubSwitches);
        switchMaster = findViewById(R.id.switchMasterSleep);
        switchSleep = findViewById(R.id.switchSleepData);
        switchSensors = findViewById(R.id.switchSensors);
        switchRaw = findViewById(R.id.switchRaw);
        switchTraining = findViewById(R.id.switchTraining);
        switchBeeps = findViewById(R.id.switchBeeps);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        btnSyncPc = findViewById(R.id.btnSyncPc);
        tvHelp = findViewById(R.id.tvHelp);
        btnUpdatePassword = findViewById(R.id.update_password);
        btnDeleteData = findViewById(R.id.delete_account);
    }

    // 1. SCARICA L'AGREEMENT PRIMA DI TUTTO
    private void fetchAgreementAndCheckState() {
        executor.execute(() -> {
            try {
                JSONObject agreementJson = NetworkClient.fetchCurrentAgreement();
                if (agreementJson.has("version") && agreementJson.has("text")) {
                    serverAgreementVersion = agreementJson.getInt("version");
                    serverAgreementText = agreementJson.getString("text");

                    mainHandler.post(this::checkCloudState);
                } else {
                    mainHandler.post(() -> lockUi("Error parsing agreement. Try again later."));
                }
            } catch (Exception e) {
                Log.e("Agreement", "Failed to fetch agreement", e);
                mainHandler.post(() -> lockUi("Network error. Could not verify Cloud status."));
            }
        });
    }

    // 2. VERIFICA STATO UTENTE
    private void checkCloudState() {
        String uuid = prefs.getUUID();
        if (uuid == null) {
            lockUi("UUID not initialized: connect Arduino to the network first.");
            return;
        }

        String pwd = prefs.getPassword();
        if (pwd == null) {
            // Nuovo utente: mostriamo l'agreement scaricato
            showAgreementDialog(uuid, true);
        } else {
            // Utente esistente: tentiamo l'autenticazione
            loadPreferences();
            authenticateOrRegister(uuid, pwd);
        }
    }

    private void lockUi(String message) {
        if (!message.isEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        for (int i = 0; i < mainLayout.getChildCount(); i++) mainLayout.getChildAt(i).setEnabled(false);
        for (int i = 0; i < layoutSubSwitches.getChildCount(); i++) layoutSubSwitches.getChildAt(i).setEnabled(false);
        btnSyncPc.setEnabled(true);
        tvHelp.setEnabled(true);
        btnUpdatePassword.setEnabled(false);
        btnDeleteData.setEnabled(false);
    }

    private void unlockUi() {
        for (int i = 0; i < mainLayout.getChildCount(); i++) mainLayout.getChildAt(i).setEnabled(true);
        for (int i = 0; i < layoutSubSwitches.getChildCount(); i++) layoutSubSwitches.getChildAt(i).setEnabled(true);
        toggleSubSwitches(switchMaster.isChecked());
        btnUpdatePassword.setEnabled(true);
        btnDeleteData.setEnabled(true);
    }

    // 3. DIALOGO DINAMICO AGREEMENT (Nuovo utente o Aggiornamento)
    private void showAgreementDialog(String uuid, boolean isNewUser) {
        String title = isNewUser ? "Terms of service" : "Updated Agreement Available";

        android.util.TypedValue typedValue = new android.util.TypedValue();

// 1. Estrae il colore del testo dinamico
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        int textColor = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT)
                ? typedValue.data : androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId);

        // 2. Estrae il colore di sfondo dinamico
        getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        int bgColor = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT)
                ? typedValue.data : androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId);

        // 1. Creiamo un titolo personalizzato e centrato
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setGravity(android.view.Gravity.CENTER); // Centratura
        titleView.setTextSize(20f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(textColor); // Usa il colore dinamico estratto prima
        titleView.setPadding(0, 50, 0, 10); // Spazio sopra e sotto il titolo

        // ------------------------------------------------------------
        // 1. Creiamo un layout personalizzato per il Dialog
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(50, 20, 50, 20);

        TextView markdownTextView = new TextView(this);
        markdownTextView.setTextSize(14f);
        markdownTextView.setTextColor(textColor);
        scrollView.addView(markdownTextView);

        // 2. Renderizziamo il Markdown usando Markwon
        Markwon markwon = Markwon.create(this);
        markwon.setMarkdown(markdownTextView, serverAgreementText);

        // 3. Costruiamo il Dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setView(scrollView) // Usiamo la nostra vista invece di setMessage()
                .setPositiveButton("I Agree", (d, which) -> {
                    if (isNewUser) {
                        showPasswordDialog(uuid);
                    } else {
                        recordAcceptanceOnServer(uuid, prefs.getPassword(), serverAgreementVersion);
                        unlockUi();
                    }
                })
                .setNegativeButton("No, thanks", (d, which) -> {
                    if (!isNewUser) {
                        switchMaster.setChecked(false);
                        prefs.setOptInGeneral(false);
                        toggleSubSwitches(false);
                        Toast.makeText(this, "Cloud Sync disabled due to unaccepted terms.", Toast.LENGTH_LONG).show();
                    }
                })
                .setCancelable(false)
                .create();

        dialog.show();

        // 4. IMPOSTAZIONI FINESTRA (Dimensioni, Gravità, Animazioni)
        Window window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setPadding(0, 0, 0, 0);

            // Applichiamo lo sfondo dinamico che abbiamo estratto all'inizio
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));

            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            // Leggermente più basso per lasciare spazio sopra
            lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.92);

            // Ancoriamo il dialog in basso per rendere naturale l'animazione
            lp.gravity = android.view.Gravity.BOTTOM;

            window.setAttributes(lp);

            // Applichiamo l'animazione che abbiamo creato
            window.setWindowAnimations(R.style.DialogAnimation);
        }

        // 5. CENTRIAMO I TASTI E AGGIUNGIAMO PADDING
        Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        if (btnPositive != null && btnNegative != null) {
            LinearLayout buttonPanel = (LinearLayout) btnPositive.getParent();
            if (buttonPanel != null) {

                // 1. Forziamo il contenitore al centro
                buttonPanel.setGravity(android.view.Gravity.CENTER);

                // 2. Troviamo la "molla" invisibile (Space) che spinge i bottoni a destra e la spegniamo
                for (int i = 0; i < buttonPanel.getChildCount(); i++) {
                    View child = buttonPanel.getChildAt(i);
                    // Intercetta qualsiasi classe Space nativa o di AndroidX
                    if (child.getClass().getSimpleName().contains("Space")) {
                        child.setVisibility(View.GONE);
                    }
                }

                // 3. Diamo un po' di margine laterale ai bottoni per non farli toccare
                LinearLayout.LayoutParams posParams = (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
                posParams.leftMargin = 20; // Distanza dal tasto negativo
                btnPositive.setLayoutParams(posParams);

                LinearLayout.LayoutParams negParams = (LinearLayout.LayoutParams) btnNegative.getLayoutParams();
                negParams.rightMargin = 20;
                btnNegative.setLayoutParams(negParams);

                // 4. Aggiungiamo 60 pixel di padding in basso (e un po' sopra per staccarli dal testo)
                buttonPanel.setPadding(
                        buttonPanel.getPaddingLeft(),
                        buttonPanel.getPaddingTop() + 30,
                        buttonPanel.getPaddingRight(),
                        buttonPanel.getPaddingBottom() + 60
                );
            }
        }
    }

    private void showPasswordDialog(String uuid) {
        final EditText input = new EditText(this);
        input.setHint("Enter a secure password");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Enter Password")
                .setMessage("If you already have a backup, enter your existing password. Otherwise, create a new one.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pwd = input.getText().toString().trim();
                    if (pwd.length() < 4) {
                        Toast.makeText(this, "Password too short", Toast.LENGTH_SHORT).show();
                        showPasswordDialog(uuid);
                    } else {
                        authenticateOrRegister(uuid, pwd);
                    }
                })
                .setCancelable(false)
                .show();
    }

    // 4. AUTENTICAZIONE E REGISTRAZIONE DELL'AGREEMENT IN BACKGROUND
    private void authenticateOrRegister(String rawUuid, String pwd) {
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                String cleanUuid = rawUuid.replaceAll("[^a-zA-Z0-9_-]", "");
                String safeUuid = java.net.URLEncoder.encode(cleanUuid, "UTF-8");
                String safePwd = java.net.URLEncoder.encode(pwd, "UTF-8");

                HttpURLConnection conn = NetworkClient.postForm("list_files.php", "uuid=" + safeUuid + "&password=" + safePwd);
                int code = conn.getResponseCode();

                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseText = is != null ? new java.util.Scanner(is).useDelimiter("\\A").next().trim() : "";

                boolean isNewUser = (code == 403) || responseText.contains("HTTP 403");

                if (isNewUser) {
                    HttpURLConnection regConn = NetworkClient.postForm("set_password.php", "uuid=" + safeUuid + "&new_password=" + safePwd);
                    int regCode = regConn.getResponseCode();
                    InputStream regIs = (regCode >= 200 && regCode < 300) ? regConn.getInputStream() : regConn.getErrorStream();
                    String regText = regIs != null ? new java.util.Scanner(regIs).useDelimiter("\\A").next().trim() : "";

                    if (regCode == 200 && !regText.contains("HTTP 40")) {
                        prefs.setPassword(pwd);
                        recordAcceptanceOnServer(cleanUuid, pwd, serverAgreementVersion);
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                            unlockUi();
                        });
                    } else {
                        mainHandler.post(() -> Toast.makeText(this, "Error creating account: " + regText, Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                if (code == 200 && responseText.startsWith("[")) {
                    prefs.setPassword(pwd);
                    int localAcceptedVersion = prefs.getAcceptedAgreementVersion();

                    mainHandler.post(() -> {
                        Toast.makeText(this, "Authenticated!", Toast.LENGTH_SHORT).show();
                        // 5. SE LA VERSIONE SERVER E' PIU' NUOVA, MOSTRA DI NUOVO IL DIALOG
                        if (localAcceptedVersion < serverAgreementVersion) {
                            showAgreementDialog(rawUuid, false);
                        } else {
                            unlockUi();
                        }
                    });
                } else if (code == 401 || responseText.contains("HTTP 401")) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Wrong password. Try again.", Toast.LENGTH_LONG).show();
                        showPasswordDialog(rawUuid);
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Unexpected response: " + responseText, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e("CloudAuth", "Java exception: ", e);
                mainHandler.post(() -> Toast.makeText(this, "Errore: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // 6. INVIO E SALVATAGGIO LOCALE DELL'ACCETTAZIONE
    private void recordAcceptanceOnServer(String uuid, String pwd, int version) {
        executor.execute(() -> {
            boolean success = NetworkClient.postAgreementAcceptance(uuid, pwd, version);
            if (success) {
                prefs.setAcceptedAgreementVersion(version);
                Log.d("Agreement", "Version " + version + " successfully recorded on server.");
            } else {
                Log.e("Agreement", "Failed to record acceptance on server.");
            }
        });
    }

    private void setupListeners() {
        switchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setOptInGeneral(isChecked);
            toggleSubSwitches(isChecked);
        });

        switchSleep.setOnCheckedChangeListener((btn, isChecked) -> prefs.setOptInSleep(isChecked));
        switchSensors.setOnCheckedChangeListener((btn, isChecked) -> prefs.setOptInSensors(isChecked));
        switchRaw.setOnCheckedChangeListener((btn, isChecked) -> prefs.setOptInRaw(isChecked));
        switchTraining.setOnCheckedChangeListener((btn, isChecked) -> prefs.setOptInTraining(isChecked));
        switchBeeps.setOnCheckedChangeListener((btn, isChecked) -> prefs.setOptInBeep(isChecked));

        btnSyncNow.setOnClickListener(v -> {
            Toast.makeText(this, "Starting Background Sync...", Toast.LENGTH_SHORT).show();
            Intent syncIntent = new Intent(this, SyncService.class);
            syncIntent.putExtra("PERFORM_DOWNLOAD", true);
            this.startForegroundService(syncIntent);
        });

        btnSyncPc.setOnClickListener(v -> sendMyFolder(v));
        tvHelp.setOnClickListener(v -> showHelpDialog());
    }

    private void toggleSubSwitches(boolean isEnabled) {
        for (int i = 0; i < layoutSubSwitches.getChildCount(); i++) {
            layoutSubSwitches.getChildAt(i).setEnabled(isEnabled);
        }
    }

    private void loadPreferences() {
        switchMaster.setChecked(prefs.isOptInGeneral());
        switchSleep.setChecked(prefs.isOptInSleep());
        switchSensors.setChecked(prefs.isOptInSensors());
        switchRaw.setChecked(prefs.isOptInRaw());
        switchTraining.setChecked(prefs.isOptInTraining());
        switchBeeps.setChecked(prefs.isOptInBeep());
        toggleSubSwitches(switchMaster.isChecked());
    }

    private void showHelpDialog() {
        String uuid = prefs.getUUID() != null ? prefs.getUUID() : "Not generated";
        new AlertDialog.Builder(this)
                .setTitle("Troubleshooting")
                .setMessage("Send an email to the maintainer, optionally attaching your UUID.\n\nNever share your password.\n\nUUID: " + uuid)
                .setPositiveButton("Copy UUID", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("UUID", uuid));
                        Toast.makeText(this, "UUID copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Contact", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("message/rfc822");
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"lollosositv+bruxismdetectorhelp@gmail.com"});
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Cloud Help");
                    intent.putExtra(Intent.EXTRA_TEXT, "Requesting cloud help for UUID: " + uuid + "\nAdd your message below:\n");
                    try { startActivity(Intent.createChooser(intent, "Send email via...")); }
                    catch (ActivityNotFoundException e) { Toast.makeText(this, "No application found.", Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    public void changePassword(View v) {
        String uuid = prefs.getUUID();
        if (uuid == null) {
            Toast.makeText(this, "UUID not found. Connect Arduino first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Layout personalizzato per inserire vecchia e nuova password
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText oldPasswordInput = new EditText(this);
        oldPasswordInput.setHint("Current Password");
        oldPasswordInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(oldPasswordInput);

        final EditText newPasswordInput = new EditText(this);
        newPasswordInput.setHint("New Password (min 4 chars)");
        newPasswordInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPasswordInput);

        new AlertDialog.Builder(this)
                .setTitle("Change Cloud Password")
                .setView(layout)
                .setPositiveButton("Update", (dialog, which) -> {
                    String oldPwd = oldPasswordInput.getText().toString().trim();
                    String newPwd = newPasswordInput.getText().toString().trim();

                    if (oldPwd.isEmpty() || newPwd.length() < 4) {
                        Toast.makeText(this, "Invalid input. Password must be at least 4 characters.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Updating password...", Toast.LENGTH_SHORT).show();

                    executor.execute(() -> {
                        try {
                            String cleanUuid = uuid.replaceAll("[^a-zA-Z0-9_-]", "");
                            String params = "uuid=" + java.net.URLEncoder.encode(cleanUuid, "UTF-8") +
                                    "&old_password=" + java.net.URLEncoder.encode(oldPwd, "UTF-8") +
                                    "&new_password=" + java.net.URLEncoder.encode(newPwd, "UTF-8");

                            HttpURLConnection conn = NetworkClient.postForm("set_password.php", params);
                            int code = conn.getResponseCode();

                            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                            String responseText = is != null ? new java.util.Scanner(is).useDelimiter("\\A").next().trim() : "";

                            if (code == 200) {
                                // Aggiorna la password criptata nelle preferenze locali
                                prefs.setPassword(newPwd);
                                mainHandler.post(() -> Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_LONG).show());
                            } else {
                                mainHandler.post(() -> Toast.makeText(this, "Failed: " + responseText, Toast.LENGTH_LONG).show());
                            }
                        } catch (Exception e) {
                            Log.e("ChangePassword", "Exception", e);
                            mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void deleteAccount(View v) {
        String uuid = prefs.getUUID();
        if (uuid == null) {
            Toast.makeText(this, "UUID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Layout di conferma di sicurezza
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Current Password");
        passwordInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        final TextView label = new TextView(this);
        label.setText("\nType exactly: I wish to delete my data");
        label.setTextSize(12);
        layout.addView(label);

        final EditText confirmInput = new EditText(this);
        confirmInput.setHint("I wish to delete my data");
        confirmInput.setText("");
        layout.addView(confirmInput);

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Delete Cloud Account")
                .setMessage("This action is irreversible. All folders associated with this UUID will be permanently wiped.")
                .setView(layout)
                .setPositiveButton("Delete Forever", (dialog, which) -> {
                    String pwd = passwordInput.getText().toString().trim();
                    String confirmationText = confirmInput.getText().toString().trim();

                    // Verifica stringa di conferma obbligatoria
                    if (!confirmationText.equals("I wish to delete my data")) {
                        Toast.makeText(this, "Confirmation text did not match. Deletion aborted.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (pwd.isEmpty()) {
                        Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Sending deletion request...", Toast.LENGTH_SHORT).show();

                    executor.execute(() -> {
                        try {
                            // Sfrutta il metodo postDeleteAccount aggiunto in NetworkClient
                            HttpURLConnection conn = NetworkClient.postDeleteAccount("delete_account.php", uuid, pwd);
                            int code = conn.getResponseCode();

                            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                            String responseText = is != null ? new java.util.Scanner(is).useDelimiter("\\A").next().trim() : "";

                            if (code == 200) {
                                // Pulizia locale dei dati di cloud sync
                                prefs.resetPassword(); // Rimuove la password salvata
                                prefs.setOptInGeneral(false); // Disattiva il master switch

                                mainHandler.post(() -> {
                                    Toast.makeText(this, "Account and server data successfully deleted.", Toast.LENGTH_LONG).show();
                                    // Opzionalmente, blocca la UI o ricarica lo stato
                                    lockUi("Account deleted.");
                                });
                            } else {
                                mainHandler.post(() -> Toast.makeText(this, "Deletion failed: " + responseText, Toast.LENGTH_LONG).show());
                            }
                        } catch (Exception e) {
                            Log.e("DeleteAccount", "Exception", e);
                            mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    public void sendMyFolder(View v) {
        new Thread(() -> {
            String serverIp = ServerDiscovery.discoverServerIP();
            if (serverIp == null) {
                Looper.prepare();
                Toast.makeText(DataSharingActivity.this, "Local server not detected.", Toast.LENGTH_LONG).show();
                return;
            }
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            FileSenderClient.sendFolder(new File(documentsDir, "RECORDINGS"), new File(documentsDir, "RECORDINGS"), serverIp, 5000, this);
        }).start();
    }

    private final BroadcastReceiver errorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String errorMsg = intent.getStringExtra("error_msg");
            if (errorMsg != null) {
                new AlertDialog.Builder(DataSharingActivity.this).setTitle("Errore Sincronizzazione").setMessage(errorMsg).setPositiveButton("OK", null).show();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.example.bruxismdetector.SYNC_ERROR");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(errorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(errorReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(errorReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        new SyncEngine(getApplicationContext()).pingCloud("EXITING_CLOUD");
        if (executor != null) {
            executor.shutdown();
        }
    }
}