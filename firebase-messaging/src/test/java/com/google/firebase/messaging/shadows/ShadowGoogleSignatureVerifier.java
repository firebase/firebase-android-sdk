package com.google.firebase.messaging.shadows;

import android.content.Context;
import com.google.android.gms.common.GoogleSignatureVerifier;
import com.google.android.gms.common.internal.Preconditions;
import org.mockito.Mockito;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link GoogleSignatureVerifier} that allows for setting the singleton object.
 *
 * <p>If the singlton object is not set, a {@link Mockito} mock will be created and used.
 *
 * <p>Use:
 *
 * <pre>{@code
 * import com.google.android.gms.common.GoogleSignatureVerifier;
 * import com.google.firebase.messaging.shadows.ShadowGoogleSignatureVerifier;
 *
 * @RunWith(GoogleRobolectricTestRunner.class)
 * @Config(shadows = {ShadowGoogleSignatureVerifier.class})
 * public class FooTest {
 *   @Mock private GoogleSignatureVerifier mMockGoogleSignatureVerifier;
 *
 *   @Before
 *   public void setUp() {
 *     // [Optional] Specify your own @Mock
 *     ShadowGoogleSignatureVerifier.setVerifier(mMockGoogleSignatureVerifier);
 *   }
 *
 *   @After
 *   public void tearDown() {
 *     ShadowGoogleSignatureVerifier.reset(); // Delete the @Mock
 *   }
 *
 *   @Test
 *   public void testGoodSignature() {
 *     // All package signatures are correct
 *     when(ShadowGoogleSignatureVerifier.getInstance().isPackageGoogleSigned(anyString()))
 *         .thenReturn(true);
 *     // .....
 *   }
 *
 *   @Test
 *   public void testBadSignature() {
 *     // All package signatures are invalid
 *     when(ShadowGoogleSignatureVerifier.getInstance().isPackageGoogleSigned(anyString()))
 *         .thenReturn(false);
 *     // .....
 *   }
 *
 *   @Test
 *   public void testFailure() {
 *     // Failure to verify signature
 *     when(ShadowGoogleSignatureVerifier.getInstance().isPackageGoogleSigned(anyString()))
 *         .thenThrow(new SecurityException("Message"));
 *     // .....
 *   }
 *
 *   @Test
 *   public void confirmNoSignatureCheckOccurred() {
 *     verifyNoMoreInteractions(ShadowGoogleSignatureVerifier.getInstance());
 *   }
 * }
 * }</pre>
 */
// TODO(b/147227580): Remove nullness suppression.
@SuppressWarnings("nullness")
@Implements(GoogleSignatureVerifier.class)
public class ShadowGoogleSignatureVerifier {

    private static GoogleSignatureVerifier sMockVerifier = null;

    public static void setVerifier(GoogleSignatureVerifier mockVerifier) {
        sMockVerifier = mockVerifier;
    }

    /** Reset the Mock used by this shadow class. */
    public static void reset() {
        sMockVerifier = null;
    }

    @Implementation
    public static GoogleSignatureVerifier getInstance(Context context) {
        return getInstance();
    }

    public static GoogleSignatureVerifier getInstance() {
        if (sMockVerifier == null) {
            sMockVerifier = Mockito.mock(GoogleSignatureVerifier.class);
        }
        return Preconditions.checkNotNull(sMockVerifier);
    }
}
