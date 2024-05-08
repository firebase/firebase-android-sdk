package com.google.firebase.firestore.local;

import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.auth.User;
import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public abstract class GlobalsCacheTest {

    private Persistence persistence;
    private GlobalsCache globalsCache;

    @Before
    public void setUp() {
        persistence = getPersistence();
        globalsCache = persistence.getGlobalsCache(User.UNAUTHENTICATED);
    }

    @After
    public void tearDown() {
        persistence.shutdown();
    }

    abstract Persistence getPersistence();

    @Test
    public void setAndGetDbToken() {
        ByteString value = ByteString.copyFrom("TestData", StandardCharsets.UTF_8);
        globalsCache.setDbToken(value);
        assertEquals(value, globalsCache.getDbToken());
    }
}
