package com.example.bruxismdetector;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class CircularEncoder {
    private static final String TAG = "CircularEncoder";
    private final MediaCodec encoder;
    private final MediaFormat format;
    private final Deque<EncodedFrame> buffer = new ArrayDeque<>();
    private final int maxFrames;
    private final Handler encoderHandler;
    private final int frameRate = 30; // Assuming 30fps

    public CircularEncoder(MediaCodec encoder, MediaFormat format, int recordingSpanMinutes) {
        this.encoder = encoder;
        this.format = format;
        this.maxFrames = recordingSpanMinutes * 60 * frameRate;

        HandlerThread thread = new HandlerThread("CircularEncoderThread");
        thread.start();
        encoderHandler = new Handler(thread.getLooper());
    }

    public void onEncodedFrame(ByteBuffer encodedData, BufferInfo bufferInfo) {
        ByteBuffer dataCopy = ByteBuffer.allocateDirect(bufferInfo.size);
        encodedData.position(bufferInfo.offset);
        encodedData.limit(bufferInfo.offset + bufferInfo.size);
        dataCopy.put(encodedData);
        dataCopy.flip();

        synchronized (buffer) {
            buffer.add(new EncodedFrame(dataCopy, bufferInfo));
            if (buffer.size() > maxFrames) {
                buffer.poll();
            }
        }
    }

    public void saveToFile(File outputFile) throws IOException {
        encoderHandler.post(() -> {
            try {
                MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int trackIndex = muxer.addTrack(format);
                muxer.start();

                synchronized (buffer) {
                    for (EncodedFrame frame : buffer) {
                        muxer.writeSampleData(trackIndex, frame.byteBuffer, frame.bufferInfo);
                    }
                }

                muxer.stop();
                muxer.release();
                Log.i(TAG, "Saved buffer to file: " + outputFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to save buffer", e);
            }
        });
    }

    private static class EncodedFrame {
        ByteBuffer byteBuffer;
        BufferInfo bufferInfo;

        EncodedFrame(ByteBuffer byteBuffer, BufferInfo bufferInfo) {
            this.byteBuffer = byteBuffer;
            this.bufferInfo = bufferInfo;
        }
    }
}
