package smile.util;

import com.amazonaws.services.lambda.runtime.Context;
import org.infinispan.creson.Factory;
import org.otrack.executor.lambda.Handler;

public class StatefulHandler extends Handler {

    public static final String CRESON="35.181.47.101:11222";

    static {
        Factory.get(CRESON);
    }

    @Override
    public byte[] handleRequest(byte[] input, Context context) {
        return super.handleRequest(input,context);
    }

}
