package com.jev.filter.service;

import com.jev.filter.client.ClassifierClient;
import com.jev.filter.dto.FilterRequest;
import com.jev.filter.dto.FilterResponse;
import com.jev.filter.exception.ClassificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FilterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterService.class);
    private static final double MINIMUM_CONFIDENCE = 0.85;

    private final ClassifierClient classifierClient;

    public FilterService(ClassifierClient classifierClient) {
        this.classifierClient = classifierClient;
    }

    public FilterResponse filter(FilterRequest request) {
        if (!classifierClient.isEnabled()) {
            LOGGER.info("Pass-through active | message will be displayed");
            return new FilterResponse(true, "testing_passthrough", null);
        }
        if (classifierClient.isObviouslyNotRelevant(request.text())) {
            LOGGER.info("Deterministic relevance gate rejected message");
            return new FilterResponse(false, "not_relevant", 0.0);
        }

        try {
            LOGGER.info("Sending message to Groq classifier");
            ClassifierClient.ClassificationResult classification =
                    classifierClient.classify(request.text());
            boolean shouldShow = classification.confidence() >= MINIMUM_CONFIDENCE
                    && isDisplayableCategory(classification.category());
            LOGGER.info("Classification complete | should_show={} | category={} | doubt_probability={}",
                    shouldShow, classification.category(), classification.confidence());
            return new FilterResponse(shouldShow, classification.category(), classification.confidence());
        } catch (ClassificationException exception) {
            LOGGER.error("Classification failed; hiding message until Groq can classify it", exception);
            return new FilterResponse(false, "error", null);
        }
    }

    private boolean isDisplayableCategory(String category) {
        return "subject_matter".equals(category) || "exam_logistics".equals(category);
    }
}
