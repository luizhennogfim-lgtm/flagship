package io.flagship.evaluation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic bucketing for percentage rollouts.
 *
 * <p>The pair (flag key, user id) is hashed with SHA-256 and mapped to a bucket from 0 to 99.
 * A user is inside a rollout of N% when bucket &lt; N. Because the bucket never changes, the same
 * user always gets the same answer, and raising the percentage only ever adds users: nobody who
 * already had the feature loses it.
 */
public final class Bucketing {

    public static final int BUCKETS = 100;

    private Bucketing() {
    }

    public static int bucket(String flagKey, String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(digest).getLong();   // first 8 bytes
            return (int) Long.remainderUnsigned(value, BUCKETS);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", ex);
        }
    }
}
