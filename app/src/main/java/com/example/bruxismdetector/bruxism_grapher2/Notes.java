package com.example.bruxismdetector.bruxism_grapher2;

public final class Notes {

    // Privato per prevenire l'istanziazione, dato che è una classe di utilità.
    private Notes() {}

    /**
     * Enum per le note musicali. I valori corrispondono agli indici dei semitoni
     * partendo da C (Do) = 0.
     */
    public static final class Note {
        public static final int C  = 0;
        public static final int Cs = 1; // C# / Db
        public static final int D  = 2;
        public static final int Ds = 3; // D# / Eb
        public static final int E  = 4;
        public static final int F  = 5;
        public static final int Fs = 6; // F# / Gb
        public static final int G  = 7;
        public static final int Gs = 8; // G# / Ab
        public static final int A  = 9;
        public static final int As = 10; // A# / Bb
        public static final int B  = 11;
    }

    /**
     * Enum per la durata delle note in millisecondi.
     */
    public static final class NoteLength {
        public static final int Whole         = 1200; // Intero
        public static final int Half          = 600;  // Metà
        public static final int Quarter       = 300;  // Quarto
        public static final int Eighth        = 150;  // Ottavo
        public static final int Sixteenth     = 75;   // Sedicesimo
        public static final int DottedQuarter = 450;  // Quarto puntato
        public static final int DottedEighth  = 225;  // Ottavo puntato
    }

    /**
     * Calcola la frequenza di una nota data la sua posizione e l'ottava.
     * La formula si basa sulla temperie equabile con A4 = 440 Hz.
     * f = 440.0 * 2^(((ottava+1)*12 + nota - 69) / 12.0)
     *
     * @param note   La nota (da Notes.Note.C a Notes.Note.B).
     * @param octave L'ottava (es. 4 per l'ottava centrale).
     * @return La frequenza della nota in Hz.
     */
    public static float getFrequency(int note, int octave) {
        double exponent = ((octave + 1) * 12 + note - 69) / 12.0;
        return (float) (440.0 * Math.pow(2.0, exponent));
    }

    // --- Costanti di Frequenza Precalcolate ---
    // (Omesse per brevità, sono identiche a prima)
    public static final float C4 = getFrequency(Note.C, 4);
    public static final float Cs4 = getFrequency(Note.Cs, 4);
    public static final float D4 = getFrequency(Note.D, 4);
    public static final float Ds4 = getFrequency(Note.Ds, 4);
    public static final float E4 = getFrequency(Note.E, 4);
    public static final float F4 = getFrequency(Note.F, 4);
    public static final float Fs4 = getFrequency(Note.Fs, 4);
    public static final float G4 = getFrequency(Note.G, 4);
    public static final float Gs4 = getFrequency(Note.Gs, 4);
    public static final float A4 = getFrequency(Note.A, 4);
    public static final float As4 = getFrequency(Note.As, 4);
    public static final float B4 = getFrequency(Note.B, 4);
    public static final float C5 = getFrequency(Note.C, 5);
    public static final float Cs5 = getFrequency(Note.Cs, 5);
    public static final float D5 = getFrequency(Note.D, 5);
    public static final float Ds5 = getFrequency(Note.Ds, 5);
    public static final float E5 = getFrequency(Note.E, 5);
    public static final float F5 = getFrequency(Note.F, 5);
    public static final float Fs5 = getFrequency(Note.Fs, 5);
    public static final float G5 = getFrequency(Note.G, 5);
    public static final float Gs5 = getFrequency(Note.Gs, 5);
    public static final float A5 = getFrequency(Note.A, 5);
    public static final float As5 = getFrequency(Note.As, 5);
    public static final float B5 = getFrequency(Note.B, 5);
    public static final float C6 = getFrequency(Note.C, 6);
    public static final float Cs6 = getFrequency(Note.Cs, 6);
    public static final float D6 = getFrequency(Note.D, 6);
    public static final float Ds6 = getFrequency(Note.Ds, 6);
    public static final float E6 = getFrequency(Note.E, 6);
    public static final float F6 = getFrequency(Note.F, 6);
    public static final float Fs6 = getFrequency(Note.Fs, 6);
    public static final float G6 = getFrequency(Note.G, 6);
    public static final float Gs6 = getFrequency(Note.Gs, 6);
    public static final float A6 = getFrequency(Note.A, 6);
    public static final float As6 = getFrequency(Note.As, 6);
    public static final float B6 = getFrequency(Note.B, 6);
    public static final float C7 = getFrequency(Note.C, 7);
    public static final float Cs7 = getFrequency(Note.Cs, 7);
    public static final float D7 = getFrequency(Note.D, 7);
    public static final float Ds7 = getFrequency(Note.Ds, 7);
    public static final float E7 = getFrequency(Note.E, 7);
    public static final float F7 = getFrequency(Note.F, 7);
    public static final float Fs7 = getFrequency(Note.Fs, 7);
    public static final float G7 = getFrequency(Note.G, 7);
    public static final float Gs7 = getFrequency(Note.Gs, 7);
    public static final float A7 = getFrequency(Note.A, 7);
    public static final float As7 = getFrequency(Note.As, 7);
    public static final float B7 = getFrequency(Note.B, 7);
    public static final float C8 = getFrequency(Note.C, 8);
    public static final float Cs8 = getFrequency(Note.Cs, 8);
    public static final float D8 = getFrequency(Note.D, 8);
    public static final float Ds8 = getFrequency(Note.Ds, 8);
    public static final float E8 = getFrequency(Note.E, 8);
    public static final float F8 = getFrequency(Note.F, 8);
    public static final float Fs8 = getFrequency(Note.Fs, 8);
    public static final float G8 = getFrequency(Note.G, 8);
    public static final float Gs8 = getFrequency(Note.Gs, 8);
    public static final float A8 = getFrequency(Note.A, 8);
    public static final float As8 = getFrequency(Note.As, 8);
    public static final float B8 = getFrequency(Note.B, 8);


    /**
     * Classe che rappresenta una melodia, equivalente alla struct 'tune' in C.
     * È pubblica per poter essere usata all'esterno, ma immutabile.
     */
    public static final class Tune {
        public final String name;
        public final int toneCount;
        public final float[] tones;
        public final int[] durations;
        public final int[] waits;

        // Costruttore aggiornato
        public Tune(String name, float[] tones, int[] durations, int[] waits) {
            this.name = name; // NUOVO
            this.tones = tones;
            this.durations = durations;
            this.waits = waits;
            this.toneCount = tones.length;
        }
    }

    // Aggiorna le definizioni delle melodie con i loro nomi
    public static final Tune drier = new Tune("Drier",
            new float[]{ G6, A6, B6, C7 },
            new int[]{ NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Half },
            new int[]{ NoteLength.Eighth, NoteLength.Eighth, NoteLength.Eighth, NoteLength.Whole * 4 }
    );

    public static final Tune samsung = new Tune("Samsung",
            new float[]{ C5, G5, C6, B5, G5 },
            new int[]{ NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter },
            new int[]{ NoteLength.DottedQuarter, NoteLength.Eighth, NoteLength.Eighth, NoteLength.DottedQuarter, NoteLength.Half }
    );

    public static final Tune apple = new Tune("Apple",
            new float[]{ 1568, 1568, 1865, 1047, 1047, 1865, 1568, 1047, 1397, 1047, 1865, 1047, 1568 },
            new int[]{ 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300 },
            new int[]{ 400, 200, 200, 200, 300, 200, 100, 100, 200, 200, 200, 200, 2000 }
    );

    public static final Tune zerb = new Tune("Zerb",
            new float[]{ F6, Gs6, F6, Gs6, F6, As6, Cs7, As6, Cs7, As6, F6, Gs6, F6, Gs6, F6, F6, Gs6, As6, C7, F6 },
            new int[]{ NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter },
            new int[]{ NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Half, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Half, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Half, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Quarter, NoteLength.Whole }
    );

    public static final Tune[] tunes = { drier, samsung, apple, zerb };
}