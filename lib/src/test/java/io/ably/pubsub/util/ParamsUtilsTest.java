package io.ably.pubsub.util;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Param;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParamsUtilsTest {

    @Test
    public void enrichParams_creates_params_if_original_are_null() throws AblyException {
        ClientOptions opts = new ClientOptions("secret_key");
        opts.pushFullWait = true;
        opts.addRequestIds = true;

        Param[] newParams = ParamsUtils.enrichParams(null, opts);

        assertEquals(2, newParams.length);
        assertTrue(Param.containsKey(newParams, "fullWait"));
        assertTrue(Param.containsKey(newParams, "request_id"));
    }

    @Test
    public void enrichParams_add_params_to_existing_ones() throws AblyException {
        ClientOptions opts = new ClientOptions("secret_key");
        opts.pushFullWait = true;
        opts.addRequestIds = true;

        Param[] originParams = Param.array(new Param("propertyName", "value"));
        Param[] newParams = ParamsUtils.enrichParams(originParams, opts);

        assertEquals(3, newParams.length);
        assertTrue(Param.containsKey(newParams, "propertyName"));
        assertTrue(Param.containsKey(newParams, "fullWait"));
        assertTrue(Param.containsKey(newParams, "request_id"));
    }

    @Test
    public void enrichParams_produce_only_requested_params() throws AblyException {
        ClientOptions opts = new ClientOptions("secret_key");
        opts.addRequestIds = true;

        Param[] originParams = Param.array(new Param("propertyName", "value"));
        Param[] newParams = ParamsUtils.enrichParams(originParams, opts);

        assertEquals(2,newParams.length);
        assertTrue(Param.containsKey(newParams, "propertyName"));
        assertTrue(Param.containsKey(newParams, "request_id"));
    }
}
