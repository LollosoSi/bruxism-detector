package com.example.bruxismdetector;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import java.io.File;

public class CameraTest extends AppCompatActivity {

    CameraRecorder recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera_test);

        recorder = new CameraRecorder();
        recorder.start(5); // 5 minutes circular buffer

        // Call this on trigger

    }

    public void save(View v){
        recorder.triggerSave(new File(getFilesDir(), "saved_segment.mp4"));
    }


}