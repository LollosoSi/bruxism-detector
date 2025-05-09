package com.example.bruxismdetector;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.se.omapi.Session;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class SwitchDialogFragment extends DialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_data_collect, container, false);
        new SwitchManager(root, requireContext());
        return root;

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        // Finish the activity when the dialog is dismissed
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.setCanceledOnTouchOutside(false);
            Window window = dialog.getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);  // Optional: center it
        }
        setCancelable(false);
    }




    public File getMostRecentFile() {
        // Get the directory path
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");

        // Check if the directory exists
        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            // Get all files in the directory
            File[] files = recordingsDir.listFiles();

            if (files != null && files.length > 0) {
                // Sort the files by last modified timestamp in descending order
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return Long.compare(file2.lastModified(), file1.lastModified());
                    }
                });

                // Return the most recent file
                return files[0];
            }
        }
        return null; // Return null if no files found or directory doesn't exist
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    File file = getMostRecentFile();
                    int count = 0;
                    // Step 1: Read the existing content
                    StringBuilder originalFirstLines = new StringBuilder();
                    StringBuilder originalContent = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (count < 2) {
                                originalFirstLines.append(line).append("\n");
                                count++;
                            }else{
                                originalContent.append(line).append("\n");
                            }

                        }
                    }

                    PrintWriter writer = new PrintWriter(new FileWriter(file));
                    writer.write(originalFirstLines.toString());


                    Intent intent = new Intent(requireContext(), Tracker2.class);

                    // Mood SeekBar
                    intent.putExtra("mood", 3);

                    // Map of switch row IDs and their keys
                    Map<Integer, String> switchKeyMap = new HashMap<>();
                    switchKeyMap.put(R.id.row_workout, "workout");
                    switchKeyMap.put(R.id.row_hydrated, "hydrated");
                    switchKeyMap.put(R.id.row_stressed, "stressed");
                    switchKeyMap.put(R.id.row_caffeine, "caffeine");
                    switchKeyMap.put(R.id.row_anxious, "anxious");
                    switchKeyMap.put(R.id.row_alcohol, "alcohol");
                    switchKeyMap.put(R.id.row_late_dinner, "late_dinner");
                    switchKeyMap.put(R.id.row_medications, "medications");
                    switchKeyMap.put(R.id.row_pain, "pain");
                    switchKeyMap.put(R.id.row_life_event, "life_event");
                    switchKeyMap.put(R.id.row_botox, "botox");


                    // Loop through switch rows and collect values
                    for (Map.Entry<Integer, String> entry : switchKeyMap.entrySet()) {
                        View row = requireView().findViewById(entry.getKey());
                        if (row != null) {
                            SwitchMaterial sw = row.findViewById(R.id.switch_item);
                            if (sw != null) {
                                intent.putExtra(entry.getValue(), sw.isChecked());
                            }
                        }
                    }

                    DailyLogData dld = new DailyLogData(intent);

                    // Step 2: Write new content
                    SessionTracker.writeDailyLog(dld, writer, requireContext(), true);

                    // Step 3: Write old content
                    writer.write(originalContent.toString());

                    writer.close();

                }catch(Exception e){
                    e.printStackTrace();
                }

                dismiss();
            }
        });
    }
}
