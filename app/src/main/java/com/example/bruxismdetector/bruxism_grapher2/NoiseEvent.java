package com.example.bruxismdetector.bruxism_grapher2;

public class NoiseEvent {
    public long millis;
    public double db;

    public NoiseEvent(long millis, double db) {
        this.millis = millis;
        this.db = db;
    }
}
