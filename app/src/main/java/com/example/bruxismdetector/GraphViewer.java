package com.example.bruxismdetector;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class GraphViewer extends AppCompatActivity {

    private File[] graphFiles;
    private ViewPager2 viewPager;
    private ImagePagerAdapter adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_graph_viewer);

        viewPager = findViewById(R.id.viewPager);
        ImageButton btnLeft = findViewById(R.id.btnLeft);
        ImageButton btnRight = findViewById(R.id.btnRight);

        graphFiles = getGraphs();
        if (graphFiles == null || graphFiles.length == 0) {
            // Handle empty or missing files here
            return;
        }

        adapter = new ImagePagerAdapter(graphFiles, scale -> {
            // Enable swipe only if scale == 1 (no zoom)
            viewPager.setUserInputEnabled(scale <= 1.2f);
            Log.i("GraphViewer", "Scale: " + scale);
        });

        viewPager.setAdapter(adapter);

        // Initially disable swipe (will be enabled when scale == 1)
        viewPager.setUserInputEnabled(true);  // Set true so user can swipe between images initially

        btnLeft.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                viewPager.setCurrentItem(current - 1, true);
            }
        });

        btnRight.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(current + 1, true);
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                // Assuming btnLeft and btnRight are accessible member variables
                btnLeft.setVisibility(position != 0 ? View.VISIBLE : View.INVISIBLE);
                btnRight.setVisibility(position != graphFiles.length-1 ? View.VISIBLE : View.INVISIBLE);

                Log.d("GraphViewer", "Page selected: " + position);
            }
        });



        viewPager.setCurrentItem(graphFiles.length-1, true);
    }


    private File[] getGraphs() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File graphDir = new File(recordingsDir, "Graphs");

        File[] files = graphDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) return null;

        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}
