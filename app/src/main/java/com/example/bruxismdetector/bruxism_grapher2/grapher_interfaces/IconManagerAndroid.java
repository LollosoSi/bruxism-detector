package com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import java.io.IOException;
import java.io.InputStream;

public class IconManagerAndroid implements IconManager<Color, Bitmap> {

    private final Context context;

    public IconManagerAndroid(Context context) {
        this.context = context;
    }

    public Bitmap recolorPng(Bitmap originalImage, int tintColor) {
        Bitmap tintedImage = Bitmap.createBitmap(originalImage.getWidth(), originalImage.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tintedImage);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_ATOP));

        canvas.drawBitmap(originalImage, 0, 0, paint);
        return tintedImage;
    }

    @Override
    public Bitmap loadImage(String imagePath, Color recolor) {
        return loadImage(imagePath, recolor.toString());
    }

    @Override
    public Bitmap loadImage(String imagePath, String recolor) {
        try (InputStream inputStream = context.getAssets().open(imagePath)) {
            Bitmap original = BitmapFactory.decodeStream(inputStream);
            return recolorPng(original, Color.parseColor(recolor));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
