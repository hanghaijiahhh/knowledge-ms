package com.myy.knowledgeembedding.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块 — 按字符数滑动窗口切分
 */
@Service
public class ChunkService {

    @Value("${chunk.max-size:500}")
    private int maxSize;

    @Value("${chunk.overlap:50}")
    private int overlap;

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            // 在句子边界处截断（优先找句号、换行）
            if (end < text.length()) {
                int cutPoint = findCutPoint(text, end, Math.max(start + 1, start + maxSize / 2));
                if (cutPoint > start) {
                    end = cutPoint;
                }
            }
            chunks.add(text.substring(start, end).trim());
            // 已到文本末尾，直接退出
            if (end >= text.length()) break;
            start = end - overlap;
            // 防止 start 不前进导致死循环（overlap 配置不当时会触发）
            if (start <= 0 || start >= end) start = end;
        }
        return chunks;
    }

    private int findCutPoint(String text, int end, int minEnd) {
        // 在 end 往前找自然断点
        for (int i = end - 1; i >= minEnd; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？' || c == ';' || c == '；') {
                return i + 1;
            }
        }
        return end;
    }
}
