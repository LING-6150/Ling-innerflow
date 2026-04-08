package com.ling.linginnerflow.multimodal;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class EmotionAnalysisResult {
    private int level;        // -1表示失败
    private double confidence; // 0.0-1.0
}