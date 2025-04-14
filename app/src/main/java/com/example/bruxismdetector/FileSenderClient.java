package com.example.bruxismdetector;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FileSenderClient {
    static final String TAG = "FileSenderClient";

    public static void sendFolder(File baseDir, File folderToSend, String serverIp, int port, Context ctx) {
        MediaScannerConnection.scanFile(ctx,
                new String[] { folderToSend.getAbsolutePath() },
                null,
                (path, uri) -> Log.d("Scan", "Scanned " + path + ":"));

        List<File> fileList = new ArrayList<>();
        collectFiles(folderToSend, fileList);

        try (Socket socket = new Socket(serverIp, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeInt(fileList.size());

            for (File file : fileList) {
                String relativePath = baseDir.toURI().relativize(file.toURI()).getPath().replace("\\", "/");
                dos.writeUTF(relativePath);
                dos.writeLong(file.length());

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[20000];
                    int read;
                    while ((read = fis.read(buffer)) > 0) {
                        dos.write(buffer, 0, read);
                    }
                }

                Log.d(TAG, "Sent: " + relativePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void collectFiles(File dir, List<File> fileList) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                collectFiles(file, fileList);
            } else {
                fileList.add(file);
            }
        }
    }
}
