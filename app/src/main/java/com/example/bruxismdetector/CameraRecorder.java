package com.example.bruxismdetector;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraRecorder {
    private MediaCodec encoder;
    private CircularEncoder circularEncoder;

    public void start(int recordingSpanMinutes) {
        // Configure MediaCodec encoder
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1280, 720);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        try {
            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            encoder.start();

            circularEncoder = new CircularEncoder(encoder, format, recordingSpanMinutes);

            new Thread(() -> {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex >= 0) {
                        ByteBuffer encodedData = encoder.getOutputBuffer(outputIndex);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoder.releaseOutputBuffer(outputIndex, false);
                            continue;
                        }
                        circularEncoder.onEncodedFrame(encodedData, bufferInfo);
                        encoder.releaseOutputBuffer(outputIndex, false);
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void triggerSave(File outputFile) {
        try {
            circularEncoder.saveToFile(outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Surface getInputSurface() {
        return encoder.createInputSurface();
    }
}
