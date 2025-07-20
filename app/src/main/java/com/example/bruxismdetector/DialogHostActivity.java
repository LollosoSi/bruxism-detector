package com.example.bruxismdetector;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

public class DialogHostActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        boolean hasSession = true;
        if(getIntent().hasExtra("hasSession")){
            hasSession = getIntent().getBooleanExtra("hasSession", true);
        }


        FragmentManager fragmentManager = getSupportFragmentManager();
        new SwitchDialogFragment().setHasSession(hasSession).show(fragmentManager, "SwitchDialog");
    }
}

