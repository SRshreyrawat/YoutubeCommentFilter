package com.jev.filter.client;

import com.jev.filter.exception.ClassificationException;

public interface ClassifierClient {

    boolean isEnabled();

    boolean isObviouslyNotRelevant(String text);

    ClassificationResult classify(String text) throws ClassificationException;

    record ClassificationResult(String category, double confidence) {
    }
}
