package io.flagship.evaluation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Called by the applications that use Flagship. Authenticate with the header "X-API-Key"
 * (a dashboard user's JWT also works, which is handy for testing).
 */
@RestController
@RequestMapping("/api/v1/evaluate")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request) {
        return service.evaluate(request);
    }
}
