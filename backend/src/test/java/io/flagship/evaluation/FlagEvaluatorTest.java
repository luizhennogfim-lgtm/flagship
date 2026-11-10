package io.flagship.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.flagship.flag.RuleOperator;
import io.flagship.flag.RuleType;

class FlagEvaluatorTest {

    private final FlagEvaluator evaluator = new FlagEvaluator();

    @Test
    void disabledFlagBeatsRulesAndPercentage() {
        FlagSnapshot flag = flag(false, 100, userListRule(true, "user-1"));

        EvaluationResult result = evaluator.evaluate(flag, EvaluationContext.ofUser("user-1"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
    }

    @Test
    void archivedFlagIsAlwaysOff() {
        FlagSnapshot archived = new FlagSnapshot(UUID.randomUUID(), "f", true, true, 100, List.of());

        EvaluationResult result = evaluator.evaluate(archived, EvaluationContext.ofUser("user-1"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_ARCHIVED);
    }

    @Test
    void fullRolloutIncludesEveryone() {
        FlagSnapshot flag = flag(true, 100);

        for (int i = 0; i < 1_000; i++) {
            EvaluationResult result = evaluator.evaluate(flag, EvaluationContext.ofUser("user-" + i));
            assertThat(result.enabled()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.PERCENTAGE_ROLLOUT);
        }
    }

    @Test
    void zeroRolloutExcludesEveryone() {
        FlagSnapshot flag = flag(true, 0);

        for (int i = 0; i < 1_000; i++) {
            EvaluationResult result = evaluator.evaluate(flag, EvaluationContext.ofUser("user-" + i));
            assertThat(result.enabled()).isFalse();
            assertThat(result.reason()).isEqualTo(EvaluationReason.NOT_IN_ROLLOUT);
        }
    }

    @Test
    void partialRolloutGivesTheSameAnswerEveryTime() {
        FlagSnapshot flag = flag(true, 50);

        for (int i = 0; i < 200; i++) {
            EvaluationContext user = EvaluationContext.ofUser("user-" + i);
            assertThat(evaluator.evaluate(flag, user)).isEqualTo(evaluator.evaluate(flag, user));
        }
    }

    @Test
    void usersInsideASmallRolloutStayInsideALargerOne() {
        FlagSnapshot at25 = flag(true, 25);
        FlagSnapshot at50 = flag(true, 50);

        for (int i = 0; i < 2_000; i++) {
            EvaluationContext user = EvaluationContext.ofUser("user-" + i);
            if (evaluator.evaluate(at25, user).enabled()) {
                assertThat(evaluator.evaluate(at50, user).enabled()).as("user-%d lost the feature", i).isTrue();
            }
        }
    }

    @Test
    void firstMatchingRuleWins() {
        FlagSnapshot flag = flag(true, 100, userListRule(false, "user-1"), userListRule(true, "user-1"));

        EvaluationResult result = evaluator.evaluate(flag, EvaluationContext.ofUser("user-1"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.RULE_MATCH);
        assertThat(result.ruleId()).isEqualTo(flag.rules().get(0).id());
    }

    @Test
    void ruleThatServesOffOverridesAFullRollout() {
        FlagSnapshot flag = flag(true, 100, userListRule(false, "blocked-user"));

        assertThat(evaluator.evaluate(flag, EvaluationContext.ofUser("blocked-user")).enabled()).isFalse();
        assertThat(evaluator.evaluate(flag, EvaluationContext.ofUser("someone-else")).enabled()).isTrue();
    }

    @Test
    void userListRuleOnlyMatchesListedUsers() {
        FlagSnapshot flag = flag(true, 0, userListRule(true, "vip-1", "vip-2"));

        assertThat(evaluator.evaluate(flag, EvaluationContext.ofUser("vip-2")).enabled()).isTrue();
        assertThat(evaluator.evaluate(flag, EvaluationContext.ofUser("regular")).enabled()).isFalse();
    }

    @Test
    void groupRuleMatchesWhenTheUserBelongsToAnyListedGroup() {
        FlagSnapshot flag = flag(true, 0, rule(RuleType.GROUP, null, null, true, "beta", "staff"));

        EvaluationContext staff = new EvaluationContext("u1", Set.of("staff", "other"), Map.of());
        EvaluationContext nobody = new EvaluationContext("u2", Set.of("other"), Map.of());

        assertThat(evaluator.evaluate(flag, staff).enabled()).isTrue();
        assertThat(evaluator.evaluate(flag, nobody).enabled()).isFalse();
    }

    @ParameterizedTest(name = "{0} [{1}] vs \"{2}\" -> {3}")
    @CsvSource({
            "EQUALS,BR,BR,true",
            "EQUALS,BR,US,false",
            "NOT_EQUALS,BR,US,true",
            "NOT_EQUALS,BR,BR,false",
            "IN,BR|AR,AR,true",
            "IN,BR|AR,US,false",
            "NOT_IN,BR|AR,US,true",
            "NOT_IN,BR|AR,BR,false",
            "CONTAINS,@acme,ana@acme.com,true",
            "CONTAINS,@acme,ana@other.com,false",
            "STARTS_WITH,ana,ana@x.com,true",
            "STARTS_WITH,ana,bob@x.com,false",
            "ENDS_WITH,.com,ana@x.com,true",
            "ENDS_WITH,.com,ana@x.org,false",
            "GREATER_THAN,18,21,true",
            "GREATER_THAN,18,17,false",
            "GREATER_THAN,18,18,false",
            "LESS_THAN,18,17,true",
            "LESS_THAN,18,21,false",
            "GREATER_THAN,18,abc,false",
            "LESS_THAN,18,abc,false"
    })
    void attributeOperators(RuleOperator operator, String ruleValues, String actual, boolean expected) {
        RuleSnapshot rule = new RuleSnapshot(UUID.randomUUID(), RuleType.ATTRIBUTE, "attr", operator,
                Arrays.asList(ruleValues.split("\\|")), true);
        FlagSnapshot flag = new FlagSnapshot(UUID.randomUUID(), "f", false, true, 0, List.of(rule));
        EvaluationContext context = new EvaluationContext("user-1", Set.of(), Map.of("attr", actual));

        assertThat(evaluator.evaluate(flag, context).enabled()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"EQUALS", "NOT_EQUALS", "IN", "NOT_IN", "CONTAINS", "GREATER_THAN", "LESS_THAN"})
    void aMissingAttributeNeverMatches(RuleOperator operator) {
        RuleSnapshot rule = new RuleSnapshot(UUID.randomUUID(), RuleType.ATTRIBUTE, "country", operator,
                List.of("1"), true);
        FlagSnapshot flag = new FlagSnapshot(UUID.randomUUID(), "f", false, true, 0, List.of(rule));

        EvaluationResult result = evaluator.evaluate(flag, EvaluationContext.ofUser("user-1"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NOT_IN_ROLLOUT);
    }

    // ---------- helpers ----------

    private static FlagSnapshot flag(boolean enabled, int percentage, RuleSnapshot... rules) {
        return new FlagSnapshot(UUID.randomUUID(), "test-flag", false, enabled, percentage, List.of(rules));
    }

    private static RuleSnapshot userListRule(boolean serve, String... users) {
        return rule(RuleType.USER_LIST, null, null, serve, users);
    }

    private static RuleSnapshot rule(RuleType type, String attribute, RuleOperator operator, boolean serve,
                                     String... values) {
        return new RuleSnapshot(UUID.randomUUID(), type, attribute, operator, List.of(values), serve);
    }
}
