package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.corpus.CorpusAssemblyService;
import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import com.ling.linginnerflow.pattern.retrieval.EvidenceRetrievalService;
import com.ling.linginnerflow.pattern.retrieval.PatternRecallService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class B3_RetrievalNoVerifyBaseline implements Baseline {

    private static final int MIN_RETRIEVED_DOCS = 3;

    private final CorpusAssemblyService corpus;
    private final PatternRecallService recall;
    private final EvidenceRetrievalService retrieval;
    private final PatternDefinitionLoader defs;
    private final EmbeddingProvider embeddingProvider;
    private final boolean live;

    public B3_RetrievalNoVerifyBaseline(
            CorpusAssemblyService corpus,
            PatternRecallService recall,
            EvidenceRetrievalService retrieval,
            PatternDefinitionLoader defs) {
        this(corpus, recall, retrieval, defs, null, Boolean.getBoolean("pattern.eval.b3.live"));
    }

    public B3_RetrievalNoVerifyBaseline(
            CorpusAssemblyService corpus,
            PatternRecallService recall,
            EvidenceRetrievalService retrieval,
            PatternDefinitionLoader defs,
            EmbeddingProvider embeddingProvider) {
        this(corpus, recall, retrieval, defs, embeddingProvider,
                Boolean.getBoolean("pattern.eval.b3.live"));
    }

    B3_RetrievalNoVerifyBaseline(
            CorpusAssemblyService corpus,
            PatternRecallService recall,
            EvidenceRetrievalService retrieval,
            PatternDefinitionLoader defs,
            EmbeddingProvider embeddingProvider,
            boolean live) {
        this.corpus = corpus;
        this.recall = Objects.requireNonNull(recall, "recall must not be null");
        this.retrieval = Objects.requireNonNull(retrieval, "retrieval must not be null");
        this.defs = Objects.requireNonNull(defs, "defs must not be null");
        this.embeddingProvider = embeddingProvider;
        this.live = live;
    }

    @Override
    public Set<PredictedPattern> predict(GTPersona persona) {
        List<CorpusDoc> docs = toCorpusDocs(persona);
        embed(docs);

        Set<PredictedPattern> predictions = new LinkedHashSet<>();
        for (String key : recall.recall(docs)) {
            List<CorpusDoc> evidence = retrieval.retrieve(key, docs).stream()
                    .filter(this::hasUsableEmbedding)
                    .limit(MIN_RETRIEVED_DOCS)
                    .toList();
            if (evidence.size() >= MIN_RETRIEVED_DOCS) {
                predictions.add(new PredictedPattern(key, Domain.valueOf(defs.get(key).getPrimaryDomain())));
            }
        }
        return predictions;
    }

    @Override
    public String name() {
        return "B3_RetrievalNoVerify";
    }

    public boolean live() {
        return live;
    }

    private List<CorpusDoc> toCorpusDocs(GTPersona persona) {
        if (persona == null || persona.corpus() == null) {
            return List.of();
        }
        return persona.corpus().stream()
                .filter(record -> record.text() != null && !record.text().isBlank())
                .map(record -> CorpusDoc.builder()
                        .docId(record.type() + ":" + record.date() + ":" + Math.abs(record.text().hashCode()))
                        .userId(persona.id())
                        .sourceType(sourceType(record.type()))
                        .sourceRef(record.date() + ":" + Math.abs(record.text().hashCode()))
                        .occurredAt(record.date().atTime(LocalTime.NOON))
                        .text(record.text())
                        .role("chat_message".equals(record.type()) ? "user" : null)
                        .crisisFlag(false)
                        .build())
                .toList();
    }

    private SourceType sourceType(String type) {
        if ("checkin".equalsIgnoreCase(type)) {
            return SourceType.checkin;
        }
        return SourceType.chat_message;
    }

    private void embed(List<CorpusDoc> docs) {
        if (docs.isEmpty()) {
            return;
        }
        if (live && corpus != null) {
            corpus.embed(docs);
            return;
        }
        if (embeddingProvider == null) {
            return;
        }
        for (CorpusDoc doc : docs) {
            doc.setEmbedding(embeddingProvider.embed(doc.getText()));
        }
    }

    private boolean hasUsableEmbedding(CorpusDoc doc) {
        float[] embedding = doc.getEmbedding();
        if (embedding == null || embedding.length == 0) {
            return false;
        }
        for (float value : embedding) {
            if (value != 0.0f) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface EmbeddingProvider {
        float[] embed(String text);
    }

}

