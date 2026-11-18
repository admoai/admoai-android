package com.admoai.sdk.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.content.Context;
import org.junit.Test;

/**
 * Java source compatibility of {@link DeviceConfig#systemDefault}. Compiling this file is the main
 * assertion: before 1.9.0 a Java app could only omit the device id with {@code systemDefault(null)},
 * and an overload taking a {@code Context} under the same Java name made that call ambiguous.
 */
public class DeviceConfigJavaSourceTest {

    @Test
    public void systemDefaultCallsFrom18xJavaSourceStillCompile() {
        DeviceConfig withNull = DeviceConfig.systemDefault(null);
        DeviceConfig noArgument = DeviceConfig.systemDefault();
        DeviceConfig withId = DeviceConfig.systemDefault("id-1");

        assertNull(withNull.getDeviceId());
        assertNull(noArgument.getDeviceId());
        assertEquals("id-1", withId.getDeviceId());
        assertFalse(withNull.getCollectCarrierAndCategory());
        assertFalse(noArgument.getCollectCarrierAndCategory());
    }

    /** Reference-only, never executed: the Java names of the overload that takes a context. */
    @SuppressWarnings("unused")
    private void contextOverloadHasItsOwnJavaName(Context context) {
        DeviceConfig signals = DeviceConfig.systemDefaultWithSignals(context);
        DeviceConfig signalsWithId = DeviceConfig.systemDefaultWithSignals(context, "id-1");
        DeviceConfig signalsWithNullId = DeviceConfig.systemDefaultWithSignals(context, null);
    }
}
