package com.example.bruxismdetector.bruxism_grapher2;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import java.util.Random;

public class TunePlayer {

    // Rimuoviamo l'interfaccia ToneAction, la logica di generazione sarà interna
    // per una gestione ottimale delle risorse.

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // --- NUOVO: Gestione centralizzata di AudioTrack ---
    private AudioTrack audioTrack;
    private final int sampleRate = 44100; // Sample rate standard

    // Stato del player
    private int currentTuneIndex = 0;
    private int currentToneIndex = 0;
    private int replays = 0;

    // Impostazioni
    private boolean rotateTunes = false;
    private int maxReplays = 10;
    private boolean isPlaying = false;

    public TunePlayer() {
        // Il costruttore è ora più semplice
    }

    /**
     * Inizializza le risorse audio. Va chiamato prima di start().
     */
    private void initializeAudio() {
        if (audioTrack != null) {
            audioTrack.release();
        }
        // Calcola la dimensione minima del buffer per lo streaming
        int bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM) // Usiamo la modalità STREAM
                .build();
    }

    /**
     * Avvia la riproduzione della melodia corrente.
     */
    public void start() {
        if (isPlaying) {
            return;
        }
        isPlaying = true;
        initializeAudio(); // Inizializza l'AudioTrack
        audioTrack.play(); // Avvia l'AudioTrack una sola volta
        currentToneIndex = -1;
        scheduleNextTone();
    }

    /**
     * Ferma la riproduzione e rilascia le risorse.
     */
    public void stop() {
        if (!isPlaying) {
            return;
        }
        isPlaying = false;
        handler.removeCallbacksAndMessages(null); // Cancella le note in coda

        if (audioTrack != null) {
            // Svuota il buffer e ferma la riproduzione in modo pulito
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }

    /**
     * Pianifica la nota successiva usando l'handler.
     */
    private void scheduleNextTone() {
        if (!isPlaying) {
            return;
        }

        // 1. Avanza alla nota successiva
        Notes.Tune currentTune = Notes.tunes[currentTuneIndex];
        currentToneIndex++;

        // 2. Controlla se la melodia è finita
        if (currentToneIndex >= currentTune.toneCount) {
            currentToneIndex = 0; // Ricomincia la melodia
            replays++;

            // 3. Controlla se bisogna cambiare melodia
            if (replays >= maxReplays) {
                replays = 0;
                resetTune(true); // Modificato per chiamare la versione corretta
                currentTune = Notes.tunes[currentTuneIndex];
            }
        }

        // 4. Ottieni i dettagli della nota corrente
        final float frequency = currentTune.tones[currentToneIndex];
        final int duration = currentTune.durations[currentToneIndex];
        final int wait = currentTune.waits[currentToneIndex];

        // 5. Genera e suona la nota immediatamente
        playToneInternal(frequency, duration);

        // 6. Pianifica la prossima chiamata a questo metodo dopo il tempo di 'wait'
        handler.postDelayed(this::scheduleNextTone, wait);
    }


    /**
     * Metodo interno che genera l'audio e lo scrive sull'AudioTrack esistente.
     * Applica un inviluppo per evitare "click" all'inizio e alla fine.
     */
    private void playToneInternal(float frequency, int durationMs) {
        if (audioTrack == null || audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }

        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        byte[] generatedSound = new byte[2 * numSamples];
        double[] sample = new double[numSamples];


        // Definiamo la durata del fade-in e fade-out in millisecondi (es. 5ms)
        int fadeDurationMs = 5;
        int fadeSamples = (int) ((fadeDurationMs / 1000.0) * sampleRate);

        // Assicuriamoci che il fade non sia più lungo della metà della nota
        if (fadeSamples > numSamples / 2) {
            fadeSamples = numSamples / 2;
        }

        // Genera l'onda sinusoidale
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i * frequency / sampleRate);
        }

        // Applica l'inviluppo (fade-in e fade-out)
        for (int i = 0; i < numSamples; ++i) {
            double multiplier = 1.0;

            // Fade-in all'inizio
            if (i < fadeSamples) {
                multiplier = (double) i / fadeSamples;
            }
            // Fade-out alla fine
            else if (i > numSamples - fadeSamples) {
                multiplier = (double) (numSamples - i) / fadeSamples;
            }

            sample[i] = sample[i] * multiplier;
        }


        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            generatedSound[idx++] = (byte) (val & 0x00ff);
            generatedSound[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Scrive i dati audio nel buffer dell'AudioTrack in streaming
        audioTrack.write(generatedSound, 0, generatedSound.length);
    }


    /**
     * Resetta la melodia corrente o ne sceglie una nuova.
     * @param forceChange Se true, cambia melodia indipendentemente da 'rotateTunes'.
     */
    public void resetTune(boolean forceChange) {
        if (rotateTunes || forceChange) {
            int newTuneIndex = currentTuneIndex;
            if (Notes.tunes.length > 1) {
                while (newTuneIndex == currentTuneIndex) {
                    newTuneIndex = random.nextInt(Notes.tunes.length);
                }
            }
            currentTuneIndex = newTuneIndex;
        }
        currentToneIndex = -1;
        replays = 0;
    }

    // --- Metodi per configurare il player ---
    public void setRotateTunes(boolean rotate) { this.rotateTunes = rotate; }
    public void setMaxReplays(int max) { this.maxReplays = max; }
    public void setCurrentTuneIndex(int index) {
        if (index >= 0 && index < Notes.tunes.length) {
            this.currentTuneIndex = index;
            resetTune(false);
        }
    }
    public boolean isPlaying() { return isPlaying; }
}