package smile.util;

import com.amazonaws.services.lambda.runtime.Context;
import org.crucial.dso.client.Client;
import org.crucial.executor.aws.AWSLambdaHandler;

public class StatefulHandler extends AWSLambdaHandler {

    static {
        Client.getClient();
    }

    @Override
    public byte[] handleRequest(byte[] input, Context context) {
        return super.handleRequest(input,context);
    }

}
