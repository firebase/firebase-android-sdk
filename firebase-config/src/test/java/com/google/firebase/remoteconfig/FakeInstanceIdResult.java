package com.google.firebase.remoteconfig;

import com.google.firebase.iid.InstanceIdResult;

/**
 * Implementation of InstanceIdResult intended for testing.
 *
 * @author Dana Silver
 */
public class FakeInstanceIdResult implements InstanceIdResult {
    private final String id;
    private final String token;

    public FakeInstanceIdResult(String id, String token) {
        this.id = id;
        this.token = token;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getToken() {
        return token;
    }
}
