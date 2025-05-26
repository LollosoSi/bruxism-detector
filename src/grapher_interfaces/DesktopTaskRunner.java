package grapher_interfaces;

import java.util.List;
import java.util.concurrent.*;

public class DesktopTaskRunner implements TaskRunner {

    private final ExecutorService executor;

    public DesktopTaskRunner(int threadCount) {
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    public DesktopTaskRunner() {
        this(Runtime.getRuntime().availableProcessors());
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
            throw new RuntimeException("Task interrupted", e);
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
