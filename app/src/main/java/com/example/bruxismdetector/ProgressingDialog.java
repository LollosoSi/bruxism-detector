package com.example.bruxismdetector;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

public class ProgressingDialog extends DialogFragment {
    private ProgressBar progressBar;
    private TextView dialogText;

    @Nullable
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.progress_dialog, null);

        progressBar = view.findViewById(R.id.dialog_progress_bar); // or spinner
        dialogText = view.findViewById(R.id.dialog_progress_text);

        builder.setView(view)
                .setCancelable(false);

        return builder.create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_Alert);
    }


    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Objects.requireNonNull(dialog.getWindow()).addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void updateProgress(int percent) {
        if (progressBar != null) {
            progressBar.setProgress(percent);
        }
    }

    public void setMessage(String message) {
        if(dialogText!=null)
            dialogText.setText(message);

    }
}