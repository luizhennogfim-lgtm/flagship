package io.flagship.evaluation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.flagship.common.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class EvaluationService {

    private final FlagSnapshotLoader loader;
    private final FlagEvaluator evaluator;
    private final MeterRegistry meters;
    private final Timer duration;

    public EvaluationService(FlagSnapshotLoader loader, FlagEvaluator evaluator, MeterRegistry meters) {
        this.loader = loader;
        this.evaluator = evaluator;
        this.meters = meters;
        this.duration = Timer.builder("flagship.evaluation.duration")
                .description("Time spent deciding one evaluation")
                .publishPercentileHistogram()
                .register(meters);
    }

    /**
     * Unknown flags and environments answer "OFF" with a reason instead of an error, so an
     * application never breaks because someone renamed or archived a flag.
     *
     * <p>Metrics are tagged by reason and outcome only. Flag keys and environments are deliberately NOT tags:
     * they are unbounded, and unbounded tag values would blow up the metrics database.
     */
    public EvaluationResponse evaluate(EvaluationRequest request) {
        long start = System.nanoTime();
        try {
            EvaluationResult result = decide(request);
            meters.counter("flagship.evaluations",
                    "reason", result.reason().name(),
                    "enabled", String.valueOf(result.enabled())).increment();
            return EvaluationResponse.from(result);
        } finally {
            duration.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private EvaluationResult decide(EvaluationRequest request) {
        UUID organizationId = TenantContext.require();
        SnapshotLookup lookup = loader.load(organizationId, request.flagKey().trim(), request.environment().trim());
        return switch (lookup.status()) {
            case FLAG_NOT_FOUND -> EvaluationResult.off(EvaluationReason.FLAG_NOT_FOUND);
            case ENVIRONMENT_NOT_FOUND -> EvaluationResult.off(EvaluationReason.ENVIRONMENT_NOT_FOUND);
            case FOUND -> evaluator.evaluate(lookup.snapshot(), contextOf(request));
        };
    }

    private static EvaluationContext contextOf(EvaluationRequest request) {
        Set<String> groups = request.groups() == null ? Set.of() : new HashSet<>(request.groups());
        Map<String, String> attributes = new HashMap<>();
        if (request.attributes() != null) {
            request.attributes().forEach((key, value) -> {
                if (key != null && value != null) {
                    attributes.put(key, value);
                }
            });
        }
        return new EvaluationContext(request.userId().trim(), groups, attributes);
    }
}
