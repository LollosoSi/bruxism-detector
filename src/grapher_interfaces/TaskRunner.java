package grapher_interfaces;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface TaskRunner {
    <T> Future<T> submit(Callable<T> task);
    <T> List<Future<T>> submitAll(List<Callable<T>> tasks);
    void shutdown();
}

