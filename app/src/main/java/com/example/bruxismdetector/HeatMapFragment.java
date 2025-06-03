package com.example.bruxismdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;

public class HeatMapFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            return inflater.inflate(R.layout.fragment_heatmap, container, false);
        }

        View root;
        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            root = view;

            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File recordingsDir = new File(documentsDir, "RECORDINGS");
            File summaryDir = new File(recordingsDir, "Summary");
            if(new File(summaryDir.getPath()+"/Heatmap.png").exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(summaryDir.getPath() + "/Heatmap.png");
                ((PhotoView) root.findViewById(R.id.photo_view_heatmap)).setImageBitmap(bitmap);
                ((PhotoView) root.findViewById(R.id.photo_view_heatmap)).setRotationTo(90.0f);
            }
        }
}
