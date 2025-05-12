package com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class GrapherAndroid extends GrapherInterface<Color, Bitmap, Typeface> {

    private Canvas canvas;
    private Paint paint;
    private Bitmap finalImage;

    public GrapherAndroid(int width, int height) {
        setImageSize(width, height);
        finalImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(finalImage);

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    @Override
    public void setColor(Color c) {
        super.setColor(c);
        paint.setColor(c.toArgb());
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    @Override
    public void drawString(String str, int x, int y) {
        canvas.drawText(str, x, y, paint);
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(x, y, x + width, y + height, paint);
        paint.setStyle(Paint.Style.FILL_AND_STROKE); // Reset to default
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + width, y + height, paint);
        paint.setStyle(Paint.Style.FILL_AND_STROKE); // Reset
    }

    @Override
    public void drawImage(Bitmap img, int x, int y, int width, int height) {
        Bitmap scaled = Bitmap.createScaledBitmap(img, width, height, true);
        canvas.drawBitmap(scaled, x, y, paint);
    }

    @Override
    public boolean writeImage(Bitmap img, String file_name) {
        FileOutputStream out = null;
        try {
            File file = new File(file_name.replace(".csv", ".png"));
            out = new FileOutputStream(file);
            img.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignore) {}
            }
        }
    }

    @Override
    public Bitmap getImage() {
        return finalImage;
    }

    @Override
    public void setFont(String fontname, int size) {
        Paint paint = new Paint();
        paint.setTextSize(size); // Convert to pixels
        paint.setTypeface(Typeface.create(fontname, Typeface.BOLD));

    }

    @Override
    public Color convertColor(String colorstring) {
        return Color.valueOf(Color.parseColor(colorstring));
    }

}
