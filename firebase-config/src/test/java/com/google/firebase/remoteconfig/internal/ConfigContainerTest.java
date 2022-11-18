package com.google.firebase.remoteconfig.internal;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class ConfigContainerTest {
    private  ConfigContainer configContainer;

    @Before
    public void setup() throws Exception {
        configContainer = ConfigContainer.newBuilder()
                .replaceConfigsWith(ImmutableMap.of("long_param", "1L", "string_param", "string_value"))
                .withAbtExperiments()
    }
}
