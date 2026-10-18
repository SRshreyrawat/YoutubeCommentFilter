package com.jev.filter.service;

import com.jev.filter.client.ClassifierClient;
import com.jev.filter.dto.FilterRequest;
import com.jev.filter.dto.FilterResponse;
import com.jev.filter.exception.ClassificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterServiceTest {

    @Test
    void returnsPassThroughResponseWhenFilteringIsDisabled() {
        FilterService service = new FilterService(new StubClassifier(false, false, null));

        FilterResponse response = service.filter(new FilterRequest("student", "hello"));

        assertEquals(new FilterResponse(true, "testing_passthrough", null), response);
    }

    @Test
    void rejectsMessagesThatFailTheDeterministicGate() {
        FilterService service = new FilterService(new StubClassifier(true, true, null));

        FilterResponse response = service.filter(new FilterRequest("student", "hello"));

        assertEquals(new FilterResponse(false, "not_relevant", 0.0), response);
    }

    @Test
    void displaysHighConfidenceAcademicClassifications() {
        FilterService service = new FilterService(new StubClassifier(
                true, false, new ClassifierClient.ClassificationResult("subject_matter", 0.94)));

        FilterResponse response = service.filter(new FilterRequest("student", "What is a cell?"));

        assertEquals(new FilterResponse(true, "subject_matter", 0.94), response);
    }

    @Test
    void failsClosedWhenClassificationFails() {
        FilterService service = new FilterService(new StubClassifier(true, false, null));

        FilterResponse response = service.filter(new FilterRequest("student", "What is a cell?"));

        assertEquals(new FilterResponse(false, "error", null), response);
    }

    private static final class StubClassifier implements ClassifierClient {

        private final boolean enabled;
        private final boolean obviouslyNotRelevant;
        private final ClassificationResult result;

        private StubClassifier(boolean enabled, boolean obviouslyNotRelevant,
                               ClassificationResult result) {
            this.enabled = enabled;
            this.obviouslyNotRelevant = obviouslyNotRelevant;
            this.result = result;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isObviouslyNotRelevant(String text) {
            return obviouslyNotRelevant;
        }

        @Override
        public ClassificationResult classify(String text) throws ClassificationException {
            if (result == null) {
                throw new ClassificationException("stub failure");
            }
            return result;
        }
    }
}
