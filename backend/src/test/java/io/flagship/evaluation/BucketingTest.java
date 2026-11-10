package io.flagship.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BucketingTest {

    @Test
    void sameFlagAndUserAlwaysGetTheSameBucket() {
        int first = Bucketing.bucket("new-checkout", "user-123");

        for (int i = 0; i < 100; i++) {
            assertThat(Bucketing.bucket("new-checkout", "user-123")).isEqualTo(first);
        }
    }

    @Test
    void bucketsStayInsideZeroToNinetyNine() {
        for (int i = 0; i < 5_000; i++) {
            assertThat(Bucketing.bucket("flag", "user-" + i)).isBetween(0, Bucketing.BUCKETS - 1);
        }
    }

    @Test
    void usersAreSpreadEvenly() {
        int users = 20_000;
        int inside = 0;
        for (int i = 0; i < users; i++) {
            if (Bucketing.bucket("spread-flag", "user-" + i) < 25) {
                inside++;
            }
        }

        // 25% of 20,000 is 5,000; the allowed band is 6+ standard deviations wide
        assertThat(inside).isBetween(4_600, 5_400);
    }

    @Test
    void raisingThePercentageOnlyAddsUsers() {
        for (int i = 0; i < 5_000; i++) {
            int bucket = Bucketing.bucket("growing-flag", "user-" + i);
            boolean in10 = bucket < 10;
            boolean in25 = bucket < 25;
            boolean in50 = bucket < 50;

            assertThat(!in10 || in25).as("user-%d in 10%% but not in 25%%", i).isTrue();
            assertThat(!in25 || in50).as("user-%d in 25%% but not in 50%%", i).isTrue();
        }
    }

    @Test
    void differentFlagsSplitUsersDifferently() {
        int sameBucket = 0;
        for (int i = 0; i < 1_000; i++) {
            if (Bucketing.bucket("flag-a", "user-" + i) == Bucketing.bucket("flag-b", "user-" + i)) {
                sameBucket++;
            }
        }

        // independent flags collide about 1% of the time (10 of 1,000)
        assertThat(sameBucket).isLessThan(60);
    }
}
