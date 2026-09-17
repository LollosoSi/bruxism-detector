package com.example.bruxismdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;

public class HeatMapFragment extends Fragment {

    private View root;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_heatmap, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;

        // Load the image on a background thread to prevent UI freezing
        loadHeatmapImage();
    }

    private void loadHeatmapImage() {
        new Thread(() -> {
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            String heatmapPath = documentsDir.getPath() + "/RECORDINGS/Summary/Heatmap.png";
            File imageFile = new File(heatmapPath);

            if (imageFile.exists()) {
                // Decode large image in background
                final Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getPath());

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        PhotoView photoView = root.findViewById(R.id.photo_view_heatmap);
                        photoView.setImageBitmap(bitmap);
                        // Ensure the image fits nicely in portrait mode
                        photoView.setRotationTo(0.0f);
                    });
                }
            } else {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::showMissingImageError);
                }
            }
        }).start();
    }

    private void showMissingImageError() {
        PhotoView photoView = root.findViewById(R.id.photo_view_heatmap);
        photoView.setVisibility(View.GONE);

        // Assuming you have an error TextView in fragment_heatmap.xml (if not, add one)
        TextView errorText = root.findViewById(R.id.errortext);
        if (errorText != null) {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText("Heatmap not found.\nPlease ensure your data is up to date and the graph has been generated.");
        }
    }
}