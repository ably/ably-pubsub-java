package io.ably.pubsub.util;

import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Param;

public class ParamsUtils {

    /**
     * Produce either new or extend provided array of parameters based on values in Client options
     *
     * @param params Array of already set parameters
     * @param options Client options
     * @return Array of parameters extended of parameters based on values in client options
     */
    public static Param[] enrichParams(Param[] params, ClientOptions options) {
        if (options.pushFullWait) {
            params = Param.push(params, "fullWait", "true");
        }
        if (options.addRequestIds) { // RSC7c
            params = Param.set(params, Crypto.generateRandomRequestId());
        }

        return params;
    }
}
