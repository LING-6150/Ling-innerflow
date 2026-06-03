package com.ling.linginnerflow.pattern.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import com.ling.linginnerflow.pattern.retrieval.EvidenceRetrievalService;
import com.ling.linginnerflow.pattern.retrieval.PatternHyDEService;
import com.ling.linginnerflow.pattern.retrieval.PatternRecallService;
import com.ling.linginnerflow.pattern.safety.LanguageFirewall;
import com.ling.linginnerflow.pattern.scoring.ConfidenceScorer;
import com.ling.linginnerflow.pattern.verify.EvidenceChainAssembler;
import com.ling.linginnerflow.pattern.verify.EvidenceVerifier;
import com.ling.linginnerflow.pattern.verify.VerificationResult;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StandalonePipeline {
    private final PatternDefinitionLoader definitions;
    private final CountingChatModel chatModel;
    private final CountingEmbeddingModel embeddingModel;
    private final PatternHyDEService hyde;
    private final PatternRecallService recall;
    private final EvidenceRetrievalService retrieval;
    private final Verifier verifier;
    private final EvidenceChainAssembler chainAssembler;
    private final ConfidenceScorer confidenceScorer;

    private StandalonePipeline(
            PatternDefinitionLoader definitions,
            CountingChatModel chatModel,
            CountingEmbeddingModel embeddingModel,
            PatternHyDEService hyde,
            PatternRecallService recall,
            EvidenceRetrievalService retrieval,
            Verifier verifier,
            EvidenceChainAssembler chainAssembler,
            ConfidenceScorer confidenceScorer) {
        this.definitions = definitions;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.hyde = hyde;
        this.recall = recall;
        this.retrieval = retrieval;
        this.verifier = verifier;
        this.chainAssembler = chainAssembler;
        this.confidenceScorer = confidenceScorer;
    }

    public static CountingChatModel createCountingChatModel() {
        String apiKey = readApiKey();
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.0)
                .build();
        OpenAiChatModel delegateChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .toolCallingManager(DefaultToolCallingManager.builder()
                        .observationRegistry(ObservationRegistry.NOOP)
                        .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
                        .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true))
                        .build())
                .retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
        return new CountingChatModel(delegateChatModel);
    }

    public static StandalonePipeline create(boolean useVerifier) {
        String apiKey = readApiKey();

        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        CountingChatModel chatModel = createCountingChatModel();

        CountingEmbeddingModel embeddingModel = createCountingEmbeddingModel(openAiApi);

        PatternDefinitionLoader definitions = new PatternDefinitionLoader();
        definitions.load();

        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        LanguageFirewall firewall = new LanguageFirewall(chatClientBuilder);
        setField(firewall, "llmJudgeEnabled", false);
        Verifier verifier = useVerifier
                ? new EvidenceVerifierAdapter(buildVerifier(chatClientBuilder, firewall))
                : new PassThroughVerifierAdapter(new PassThroughVerifier());

        return new StandalonePipeline(
                definitions,
                chatModel,
                embeddingModel,
                new PatternHyDEService(definitions, embeddingModel),
                new PatternRecallService(definitions),
                new EvidenceRetrievalService(definitions),
                verifier,
                new EvidenceChainAssembler(),
                buildScorer());
    }

    public static CountingEmbeddingModel createCountingEmbeddingModel() {
        return createCountingEmbeddingModel(OpenAiApi.builder().apiKey(readApiKey()).build());
    }

    private static CountingEmbeddingModel createCountingEmbeddingModel(OpenAiApi openAiApi) {
        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-3-small")
                .build();
        OpenAiEmbeddingModel delegateEmbeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                embeddingOptions,
                RetryUtils.SHORT_RETRY_TEMPLATE,
                ObservationRegistry.NOOP);
        return new CountingEmbeddingModel(delegateEmbeddingModel);
    }

    /** Build ConfidenceScorer with V1.2 default weights (since Spring isn't injecting them). */
    private static ConfidenceScorer buildScorer() {
        ConfidenceScorer s = new ConfidenceScorer();
        setField(s, "wEvidence", 0.50);
        setField(s, "wRecurrence", 0.30);
        setField(s, "wRecency", 0.20);
        setField(s, "surfaceThreshold", 0.6);
        return s;
    }

    public PipelineResult predict(GTPersona persona) {
        chatModel.reset();
        embeddingModel.reset();

        List<CorpusDoc> docs = toCorpusDocs(persona);
        embedCorpus(docs);
        PipelineTrace trace = new PipelineTrace();

        Set<PredictedPattern> predictions = new LinkedHashSet<>();
        Set<String> recalled = recall.recall(docs);
        for (String patternKey : definitions.keys().stream().sorted().toList()) {
            PatternTrace patternTrace = new PatternTrace(patternKey);
            patternTrace.setRecallHit(recalled.contains(patternKey));
            trace.patterns().put(patternKey, patternTrace);
        }

        for (String patternKey : recalled) {
            PatternDefinition definition = definitions.get(patternKey);
            PatternTrace patternTrace = trace.patterns().get(patternKey);
            hyde.exemplarVectors(patternKey);
            List<CorpusDoc> retrieved = retrieval.retrieve(patternKey, docs).stream()
                    .filter(doc -> doc.getEmbedding() != null && doc.getEmbedding().length > 0)
                    .toList();
            patternTrace.setRetrievedDocIds(retrieved.stream().map(CorpusDoc::getDocId).toList());

            List<VerificationResult> verified = verifier.verify(patternKey, definition, retrieved);
            patternTrace.setVerifierResults(verified.stream()
                    .map(VerifierTrace::from)
                    .toList());

            Optional<EvidenceChainAssembler.AssembledChain> chain = chainAssembler.assemble(
                    patternKey,
                    UUID.randomUUID().toString(),
                    verified,
                    retrieved,
                    Domain.valueOf(definition.getPrimaryDomain()));
            if (chain.isEmpty()) {
                patternTrace.setConfidence(0.0);
                patternTrace.setSurface(false);
                continue;
            }

            double confidence = confidenceScorer.score(chain.get().items());
            boolean surface = confidenceScorer.shouldSurface(confidence);
            patternTrace.setConfidence(confidence);
            patternTrace.setSurface(surface);
            patternTrace.setDomain(chain.get().domain());
            patternTrace.setEvidenceItems(chain.get().items().stream().map(EvidenceTrace::from).toList());
            if (surface) {
                predictions.add(new PredictedPattern(patternKey, chain.get().domain()));
            }
        }

        return new PipelineResult(Set.copyOf(predictions), trace, TokenUsage.sum(chatModel.usage(), embeddingModel.usage()));
    }

    private List<CorpusDoc> toCorpusDocs(GTPersona persona) {
        List<CorpusDoc> docs = new ArrayList<>();
        int index = 0;
        for (CorpusRecord record : persona.corpus()) {
            if (record.text() == null || record.text().isBlank()) {
                index++;
                continue;
            }
            String sourceRef = persona.id() + "-" + index;
            SourceType sourceType = sourceType(record.type());
            docs.add(CorpusDoc.builder()
                    .docId(sourceType + ":" + sourceRef)
                    .userId(persona.id())
                    .sourceType(sourceType)
                    .sourceRef(sourceRef)
                    .occurredAt(record.date().atStartOfDay())
                    .text(record.text())
                    .role("user")
                    .crisisFlag(isCrisis(record.text(), persona.crisisSeeds()))
                    .build());
            index++;
        }
        return docs;
    }

    private SourceType sourceType(String raw) {
        return switch (raw) {
            case "journal" -> SourceType.journal_entry;
            case "checkin" -> SourceType.checkin;
            default -> SourceType.chat_message;
        };
    }

    private boolean isCrisis(String text, List<String> crisisSeeds) {
        return crisisSeeds.stream().filter(Objects::nonNull).anyMatch(text::contains);
    }

    private void embedCorpus(List<CorpusDoc> docs) {
        if (docs.isEmpty()) {
            return;
        }
        List<String> texts = docs.stream().map(CorpusDoc::getText).toList();
        List<float[]> vectors = embeddingModel.embed(texts);
        for (int i = 0; i < docs.size() && i < vectors.size(); i++) {
            docs.get(i).setEmbedding(vectors.get(i));
        }
    }

    public record PipelineResult(Set<PredictedPattern> predictions, PipelineTrace trace, TokenUsage tokenUsage) {}

    public record TokenUsage(long chatPromptTokens, long chatCompletionTokens, long embeddingTokens) {
        public long chatTokens() {
            return chatPromptTokens + chatCompletionTokens;
        }

        public long totalTokens() {
            return chatTokens() + embeddingTokens;
        }

        public static TokenUsage zero() {
            return new TokenUsage(0, 0, 0);
        }

        public static TokenUsage sum(TokenUsage left, TokenUsage right) {
            return new TokenUsage(
                    left.chatPromptTokens + right.chatPromptTokens,
                    left.chatCompletionTokens + right.chatCompletionTokens,
                    left.embeddingTokens + right.embeddingTokens);
        }
    }

    public static class PipelineTrace {
        private final Map<String, PatternTrace> patterns = new LinkedHashMap<>();

        public Map<String, PatternTrace> patterns() {
            return patterns;
        }
    }

    public static class PatternTrace {
        private final String patternKey;
        private boolean recallHit;
        private List<String> retrievedDocIds = List.of();
        private List<VerifierTrace> verifierResults = List.of();
        private List<EvidenceTrace> evidenceItems = List.of();
        private Domain domain;
        private double confidence;
        private boolean surface;

        PatternTrace(String patternKey) {
            this.patternKey = patternKey;
        }

        public String patternKey() { return patternKey; }
        public boolean recallHit() { return recallHit; }
        public List<String> retrievedDocIds() { return retrievedDocIds; }
        public List<VerifierTrace> verifierResults() { return verifierResults; }
        public List<EvidenceTrace> evidenceItems() { return evidenceItems; }
        public Domain domain() { return domain; }
        public double confidence() { return confidence; }
        public boolean surface() { return surface; }
        void setRecallHit(boolean recallHit) { this.recallHit = recallHit; }
        void setRetrievedDocIds(List<String> retrievedDocIds) { this.retrievedDocIds = List.copyOf(retrievedDocIds); }
        void setVerifierResults(List<VerifierTrace> verifierResults) { this.verifierResults = List.copyOf(verifierResults); }
        void setEvidenceItems(List<EvidenceTrace> evidenceItems) { this.evidenceItems = List.copyOf(evidenceItems); }
        void setDomain(Domain domain) { this.domain = domain; }
        void setConfidence(double confidence) { this.confidence = confidence; }
        void setSurface(boolean surface) { this.surface = surface; }
    }

    public record VerifierTrace(
            String docId,
            boolean supports,
            boolean isVerbatim,
            String verbatimSpan,
            String interpretation,
            Domain inferredDomain) {
        static VerifierTrace from(VerificationResult result) {
            return new VerifierTrace(
                    result.getDocId(),
                    result.isSupports(),
                    result.isVerbatimQuotable(),
                    result.getVerbatimSpan(),
                    result.getInterpretation(),
                    result.getInferredDomain());
        }
    }

    public record EvidenceTrace(
            SourceType sourceType,
            String sourceRef,
            String excerpt,
            boolean verbatim,
            String interpretation) {
        static EvidenceTrace from(EvidenceItem item) {
            return new EvidenceTrace(
                    item.getSourceType(),
                    item.getSourceRef(),
                    item.getExcerpt(),
                    item.isVerbatim(),
                    item.getInterpretation());
        }
    }

    interface Verifier {
        List<VerificationResult> verify(String patternKey, PatternDefinition definition, List<CorpusDoc> docs);
    }

    record EvidenceVerifierAdapter(EvidenceVerifier delegate) implements Verifier {
        @Override
        public List<VerificationResult> verify(String patternKey, PatternDefinition definition, List<CorpusDoc> docs) {
            return delegate.verify(patternKey, definition, docs);
        }
    }

    record PassThroughVerifierAdapter(PassThroughVerifier delegate) implements Verifier {
        @Override
        public List<VerificationResult> verify(String patternKey, PatternDefinition definition, List<CorpusDoc> docs) {
            return delegate.verify(patternKey, definition, docs);
        }
    }

    public static class CountingChatModel implements ChatModel {
        private final ChatModel delegate;
        private TokenUsage usage = TokenUsage.zero();

        CountingChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponse response = delegate.call(prompt);
            Usage responseUsage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            if (responseUsage != null) {
                usage = new TokenUsage(
                        usage.chatPromptTokens + safe(responseUsage.getPromptTokens()),
                        usage.chatCompletionTokens + safe(responseUsage.getCompletionTokens()),
                        usage.embeddingTokens);
            }
            sleepBetweenLiveCalls();
            return response;
        }

        TokenUsage usage() { return usage; }
        void reset() { usage = TokenUsage.zero(); }
    }

    public static class CountingEmbeddingModel implements EmbeddingModel {
        private final OpenAiEmbeddingModel delegate;
        private TokenUsage usage = TokenUsage.zero();

        CountingEmbeddingModel(OpenAiEmbeddingModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public float[] embed(Document document) {
            return delegate.embed(document);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            EmbeddingResponse response = delegate.call(request);
            Usage responseUsage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            if (responseUsage != null) {
                long total = safe(responseUsage.getTotalTokens());
                if (total == 0) {
                    total = safe(responseUsage.getPromptTokens());
                }
                usage = new TokenUsage(usage.chatPromptTokens, usage.chatCompletionTokens, usage.embeddingTokens + total);
            }
            sleepBetweenLiveCalls();
            return response;
        }

        TokenUsage usage() { return usage; }
        void reset() { usage = TokenUsage.zero(); }
    }

    private static long safe(Integer value) {
        return value == null ? 0L : value;
    }

    private static void sleepBetweenLiveCalls() {
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static Set<PredictedPattern> parsePredictions(String raw, PatternDefinitionLoader definitions) {
        String json = extractJsonArray(raw);
        if (json == null) {
            return Set.of();
        }
        try {
            List<Map<String, String>> rows = new ObjectMapper().readValue(json, new TypeReference<>() {});
            Set<PredictedPattern> parsed = new LinkedHashSet<>();
            for (Map<String, String> row : rows) {
                String patternKey = normalize(row.get("pattern_key"));
                String domain = normalize(row.get("domain"));
                if (patternKey == null || domain == null || !definitions.keys().contains(patternKey)) {
                    continue;
                }
                try {
                    parsed.add(new PredictedPattern(patternKey, Domain.valueOf(domain)));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed row
                }
            }
            return Set.copyOf(parsed);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static String extractJsonArray(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Reads the personal OpenAI API key for LIVE eval.
     *
     * Priority:
     *   1. {@code MY_OPENAI_KEY}  — preferred. Use this so it never collides with
     *      Codex CLI's {@code OPENAI_API_KEY} (which routes through the company
     *      proxy `llm-center.ali.modelbest.cn` and won't accept a real OpenAI key).
     *   2. {@code PERSONAL_OPENAI_KEY} — alias.
     *   3. {@code OPENAI_API_KEY} — fallback for users without Codex CLI installed.
     */
    static String readApiKey() {
        String[] candidates = { "MY_OPENAI_KEY", "PERSONAL_OPENAI_KEY", "OPENAI_API_KEY" };
        for (String name : candidates) {
            String v = System.getenv(name);
            if (v != null && !v.isBlank()) return v.trim();
        }
        throw new IllegalStateException(
            "No API key found. Set one of: MY_OPENAI_KEY (preferred), " +
            "PERSONAL_OPENAI_KEY, or OPENAI_API_KEY (only safe if Codex CLI " +
            "is not configured to use the same variable)."
        );
    }

    /**
     * Build an EvidenceVerifier with all its @Value-injected fields set to defaults.
     * Standalone path skips Spring, so these fields are null until we set them.
     */
    private static EvidenceVerifier buildVerifier(ChatClient.Builder builder, LanguageFirewall firewall) {
        EvidenceVerifier v = new EvidenceVerifier(builder, new ObjectMapper(), firewall);
        setField(v, "modeStr", "BATCH");
        return v;
    }

    /** Reflectively set a private field to a default value (only for @Value fields). */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set " + fieldName + " on " + target.getClass().getSimpleName(), e);
        }
    }
}
