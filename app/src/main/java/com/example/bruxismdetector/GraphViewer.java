package com.example.bruxismdetector;

import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class GraphViewer extends AppCompatActivity {

        private File[] graphFiles;

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
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });

            graphFiles = getGraphs(); // Your sorted File[] source
            if(graphFiles==null)
                return;
            ViewPager2 viewPager = findViewById(R.id.viewPager);
            ImagePagerAdapter adapter = new ImagePagerAdapter(graphFiles);
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(graphFiles.length-1, false); // Start at index 0
        }

        // Stub for illustration; implement your file-sorting logic here
        public File[] getGraphs() {

            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File recordingsDir = new File(documentsDir, "RECORDINGS");
            File graphRecordingsDirectory = new File(recordingsDir, "Graphs");

            File[] files = graphRecordingsDirectory.listFiles((d, name) -> name.endsWith(".png"));
            if(files==null)
                return null;
            Arrays.sort(files, Comparator.comparing(File::getName));
            return files;
        }
}