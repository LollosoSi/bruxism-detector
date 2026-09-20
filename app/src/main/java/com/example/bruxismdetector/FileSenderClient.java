package com.example.bruxismdetector;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileSenderClient {
    static final String TAG = "FileSenderClient";

    public static void sendFolder(File baseDir, File folderToSend, String serverIp, int port, Context ctx) {
        MediaScannerConnection.scanFile(ctx, new String[]{folderToSend.getAbsolutePath()}, null, null);

        List<File> fileList = new ArrayList<>();
        collectFiles(folderToSend, fileList);

        if (fileList.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, "No files found to send.", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            // IL SEGRETO: Apriamo un solo Socket e lo trasformiamo in un tubo ZIP
            try (Socket socket = new Socket(serverIp, port);
                 BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
                 ZipOutputStream zos = new ZipOutputStream(bos)) {

                for (File file : fileList) {
                    String relativePath = baseDir.toURI().relativize(file.toURI()).getPath().replace("\\", "/");

                    // Diciamo al flusso ZIP che sta arrivando un nuovo file
                    ZipEntry zipEntry = new ZipEntry(relativePath);
                    zos.putNextEntry(zipEntry);

                    // Riversiamo il contenuto del file piccolo nel flusso di rete
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[65536]; // Buffer gigante da 64KB
                        int length;
                        while ((length = fis.read(buffer)) >= 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    zos.closeEntry(); // Chiudiamo il file all'interno dello ZIP

                    Log.d(TAG, "Streamed: " + relativePath);
                }

                // Fine del trasferimento
                zos.finish();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ctx, "Sync Complete!", Toast.LENGTH_LONG).show()
                );

            } catch (IOException e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ctx, "Sync Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private static void collectFiles(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectFiles(file, fileList);
                } else {
                    fileList.add(file);
                }
            }
        }
    }
}