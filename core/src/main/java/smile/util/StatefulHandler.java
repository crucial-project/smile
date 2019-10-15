package smile.util;

import com.amazonaws.services.lambda.runtime.Context;
import org.infinispan.creson.Factory;
import eu.cloudbutton.executor.lambda.Handler;

public class StatefulHandler extends Handler {

    static {
        Factory.get(ServerlessExecutor.CRESON);
    }

    @Override
    public byte[] handleRequest(byte[] input, Context context) {
        return super.handleRequest(input,context);
    }

}
