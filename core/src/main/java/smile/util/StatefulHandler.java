package smile.util;

import com.amazonaws.services.lambda.runtime.Context;
import org.crucial.dso.Factory;
import org.crucial.executor.aws.AWSLambdaHandler;

public class StatefulHandler extends AWSLambdaHandler {

    static {
        Factory.get(ServerlessExecutor.CRUCIAL);
    }

    @Override
    public byte[] handleRequest(byte[] input, Context context) {
        return super.handleRequest(input,context);
    }

}
