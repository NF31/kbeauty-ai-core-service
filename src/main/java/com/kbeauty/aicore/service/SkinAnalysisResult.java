package com.kbeauty.aicore.service;

import java.util.Map;

public record SkinAnalysisResult(Map<String, Integer> scores, String analysis) {
}
