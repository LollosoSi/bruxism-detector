package com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AndroidTaskRunner implements TaskRunner {

    private final ExecutorService executor;

    public AndroidTaskRunner() {
        int cores = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(cores);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> submitAll(List<Callable<T>> tasks) {
        try {
            return executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException("Tasks interrupted", e);
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
