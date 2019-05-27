package smile.util;

import com.amazonaws.services.lambda.runtime.Context;
import org.infinispan.creson.Factory;
import eu.cloudbutton.executor.ServerlessExecutorService;
import eu.cloudbutton.executor.lambda.AWSLambdaExecutorService;
import eu.cloudbutton.executor.lambda.Handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ServerlessExecutor{

    public static final String CRESON="35.181.47.101:11222";

    private static ServerlessExecutorService service;

    public static void createThreadPool(ServerlessExecutorService s) {
        Factory.get(CRESON);
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
