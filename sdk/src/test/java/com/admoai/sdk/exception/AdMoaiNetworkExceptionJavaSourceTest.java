package com.admoai.sdk.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.admoai.sdk.model.common.Error;
import java.util.Collections;
import org.junit.Test;

/**
 * Java source compatibility of {@link AdMoaiNetworkException}. Compiling this file is the main
 * assertion: the 3-argument constructor of 1.8.x stays callable from Java, next to the 4-argument
 * one that carries the engine's error codes.
 */
public class AdMoaiNetworkExceptionJavaSourceTest {

    @Test
    public void theConstructorsFrom18xJavaSourceStillCompile() {
        AdMoaiNetworkException old = new AdMoaiNetworkException("Server error: 503", null, 503);
        assertEquals(Integer.valueOf(503), old.getStatusCode());
        assertTrue(old.getErrors().isEmpty());

        AdMoaiNetworkException noStatus = new AdMoaiNetworkException("offline", null, null);
        assertNull(noStatus.getStatusCode());
    }

    @Test
    public void javaReadsTheEngineErrorCodes() {
        Error error = new Error(10009, "The body contains an unknown field \"carrier\"");
        AdMoaiNetworkException e =
            new AdMoaiNetworkException("Client error: 400", null, 400, Collections.singletonList(error));
        assertEquals(10009, e.getErrors().get(0).getCode());
    }
}
