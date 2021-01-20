package smile.util;

import org.crucial.dso.Factory;
import org.crucial.executor.ServerlessExecutorService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ServerlessExecutor{

    public static final String CRUCIAL ="34.89.33.39:11222";

    private static ServerlessExecutorService service;

    public static void createThreadPool(ServerlessExecutorService s) {
        Factory.get(CRUCIAL);
        service = s;
    }

    public static <T> List<T> run(Collection<? extends Callable<T>> tasks) throws Exception {
        List<T> results = new ArrayList<>();

        List<Future<T>> futures = service.invokeAll(tasks);
        for (Future<T> future : futures) {
            results.add(future.get());
        }

        return results;
    }

    public static void shutdown() {
        Factory.getSingleton().clear();
        Factory.getSingleton().close();
    }

}
