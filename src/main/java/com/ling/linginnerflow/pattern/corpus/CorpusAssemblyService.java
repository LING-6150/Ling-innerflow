package com.ling.linginnerflow.pattern.corpus;

import com.ling.linginnerflow.checkin.CheckIn;
import com.ling.linginnerflow.checkin.CheckInRepository;
import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorpusAssemblyService {

    private static final int WINDOW_DAYS = 180;
    private static final int WINDOW_LIMIT = 200;

    private final ChatMessageRepository chatMessageRepository;
    private final CheckInRepository checkInRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${pattern.gate.min-corpus-docs:20}")
    private int minCorpusDocs;

    public List<CorpusDoc> assemble(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<CorpusDoc> docs = new ArrayList<>();

        for (ChatMessage message : chatMessageRepository.findUserMessagesSince(userId, since)) {
            if (!"user".equals(message.getRole())) {
                continue;
            }
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            docs.add(CorpusDoc.builder()
                    .docId(SourceType.chat_message.name() + ":" + message.getId())
                    .userId(userId)
                    .sourceType(SourceType.chat_message)
                    .sourceRef(String.valueOf(message.getId()))
                    .occurredAt(message.getCreatedAt())
                    .text(message.getContent())
                    .role(message.getRole())
                    .crisisFlag(message.getEmotionLevel() != null && message.getEmotionLevel() == 5)
                    .build());
        }

        for (CheckIn checkIn : checkInRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (checkIn.getCreatedAt() == null || checkIn.getCreatedAt().isBefore(since)) {
                continue;
            }
            if (checkIn.getContent() == null || checkIn.getContent().isBlank()) {
                continue;
            }
            docs.add(CorpusDoc.builder()
                    .docId(SourceType.checkin.name() + ":" + checkIn.getId())
                    .userId(userId)
                    .sourceType(SourceType.checkin)
                    .sourceRef(String.valueOf(checkIn.getId()))
                    .occurredAt(checkIn.getCreatedAt())
                    .text(checkIn.getContent())
                    .crisisFlag(checkIn.getEmotionLevel() != null && checkIn.getEmotionLevel() == 5)
                    .build());
        }

        docs.sort(Comparator.comparing(CorpusDoc::getOccurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        if (docs.size() > WINDOW_LIMIT) {
            docs = new ArrayList<>(docs.subList(docs.size() - WINDOW_LIMIT, docs.size()));
        }

        log.info("[PatternCorpus] assembled userId={}, docs={}", userId, docs.size());
        return docs;
    }

    public void embed(List<CorpusDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        try {
            List<String> texts = docs.stream()
                    .map(CorpusDoc::getText)
                    .toList();
            List<float[]> vectors = embeddingModel.embed(texts);
            for (int i = 0; i < docs.size() && i < vectors.size(); i++) {
                docs.get(i).setEmbedding(vectors.get(i));
            }
        } catch (Exception e) {
            log.warn("[PatternCorpus] embedding unavailable, continuing without vectors: {}", e.getMessage());
        }
    }

    public boolean meetsGate(List<CorpusDoc> docs) {
        return docs != null && docs.size() >= minCorpusDocs;
    }
}
