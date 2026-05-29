# HANDOFF — Pattern Engine V1.2 implementation (for Codex)

Worktree: `/Users/apple/Documents/Codex/2026-05-29/Ling-innerflow/.claude/worktrees/pattern-engine-v1`
Branch: `worktree-pattern-engine-v1`. Do all work here.

## Authoritative specs (do NOT redesign the product)
- `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md` — FROZEN product spec (§7 taxonomy, §8 schema, §9 evidence, §10 review, §13 safety, §14 scope)
- `docs/superpowers/specs/2026-05-29-pattern-engine-v1.md` — engine design (S0-S9 §1.2, retrieval §3, verify §4, confidence §5, dedup §6, eval §7-9)
- `docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md` — FINAL; where it conflicts with priors, V1.2 WINS
- `docs/ARCHITECTURE_REVIEW.md` — reuse memory/RAG without inheriting clinical framing; do NOT overload UserMemory

## Hard rules carried in the code already
- Separate subsystem (own entities, not UserMemory). Closed-set: 12 pattern_keys, 6 domains.
- Evidence chain: ≥3 items, ≥1 verbatim; verbatim span MUST be exact substring of source (asserted in EvidenceVerifier).
- Confidence has NO LLM 'strength' term (V1.2 R13): `0.50*Evidence + 0.30*Recurrence + 0.20*Recency`.
- Verifier (R16'): whole-system BATCH or SINGLE_ITEM mode; NO per-item shuffle-drop.
- Crisis-flagged docs (emotionLevel==5) never become evidence. Only role='user' chat turns are evidence-eligible.

## DONE — already on disk (verify, then build against these)
Java (src/main/java/com/ling/linginnerflow/pattern/):
- domain/{Domain,PatternStatus,SourceType}.java
- entity/{PatternInstance,EvidenceChain,EvidenceItem}.java
- repo/{PatternInstanceRepository,EvidenceChainRepository,EvidenceItemRepository}.java
- definition/{PatternDefinition,PatternDefinitionLoader}.java   (loads 12 YAMLs, fail-fast)
- corpus/CorpusDoc.java                                          (docId,userId,sourceType,sourceRef,occurredAt,text,role,crisisFlag,embedding)
- retrieval/{PatternHyDEService,PatternRecallService,EvidenceRetrievalService}.java
    - PatternHyDEService.exemplarVectors(String patternKey) -> List<float[]>
    - PatternRecallService.recall(List<CorpusDoc>) -> Set<String>
    - EvidenceRetrievalService.retrieve(String patternKey, List<CorpusDoc>) -> List<CorpusDoc>  (top-7)
- verify/{VerificationResult,EvidenceVerifier,EvidenceChainAssembler}.java
    - EvidenceVerifier.verify(String patternKey, PatternDefinition def, List<CorpusDoc> docs) -> List<VerificationResult>
    - record AssembledChain(EvidenceChain chain, List<EvidenceItem> items, Domain domain)
    - EvidenceChainAssembler.assemble(String patternKey, String patternInstanceId, List<VerificationResult>, List<CorpusDoc>, Domain primaryDomain) -> Optional<AssembledChain>
- scoring/ConfidenceScorer.java   score(List<EvidenceItem>) -> double ; shouldSurface(double) -> boolean
- dedup/PatternDeduplicator.java  findDuplicate(String newSummary, String newPatternKey, List<PatternInstance> existingActive) -> PatternInstance|null (0.88)
- safety/LanguageFirewall.java    isClean(String) ; enforce(String) ; SAFE_PHRASING_TEMPLATE

Resources: src/main/resources/patterns/*.yaml  (all 12 keys present, with lexical_cues + hyde_exemplars)
Eval data: eval/groundtruth/tierA/{a-01..a-06}.{answerkey.yaml,corpus.md} + MANIFEST.md
           eval/groundtruth/sealed/{README, TEMPLATE_*, ah-01.*}   (Tier A-H — HUMAN fills in; never LLM-touch)

## TODO — remaining work, in dependency order

### 1. S0 corpus assembly (BLOCKS the orchestrator)
Create `pattern/corpus/CorpusAssemblyService.java` — @Service @Slf4j @RequiredArgsConstructor.
Inject ChatMessageRepository, CheckInRepository, EmbeddingModel.
- assemble(String userId): chat via `findUserMessagesSince(userId, now.minusDays(180))` (role='user' only → CorpusDoc,
  crisisFlag = emotionLevel!=null && emotionLevel==5); check-ins via a NEW additive repo method
  `CheckInRepository.findByUserIdOrderByCreatedAtAsc(String)`. Merge, sort by occurredAt, window = min(last 200, within 180d).
- embed(List<CorpusDoc>): batch embeddingModel.embed(texts), set .embedding; on exception log.warn, leave null (never throw).
- @Value("${pattern.gate.min-corpus-docs:20}") int minCorpusDocs; meetsGate(list) -> size>=minCorpusDocs.
Reuse exact existing signatures (read websocket/ChatMessage*.java, checkin/CheckIn*.java).

### 2. Orchestration + API
- `pattern/service/PatternDiscoveryService.java` @Service @Transactional — implements S0-S9 (engine §1.2 + V1.2):
  assemble+embed → gate → recall → per candidate: retrieve → verify → assemble chain → score + LLM personalized_summary
  (grounded ONLY on verified excerpts) → firewall (regen ≤2 else discard) → dedup vs active → cooldown (90d reject,
  Jaccard<0.5 substantial-difference over source_refs) → upsert PatternInstance + new EvidenceChain in one tx.
  Respect (userId,patternKey,domain) active-uniqueness; bump refreshCount; never delete confirmed. Add Jaccard(Set,Set) helper.
- `pattern/service/PatternReviewService.java` — confirm/partial/reject/defer/archive/editSummaryOrNote per product §10.
  reject sets lastReviewedAt (cooldown anchor). confirm leaves a TODO hook for PatternFact→UserWiki (do NOT modify UserMemory now).
- `pattern/controller/PatternController.java` @RequestMapping("/api/pattern") + dto/ :
  GET /instances?domain=&status= (exclude hidden); GET /instances/{id}/evidence; POST /instances/{id}/review;
  PATCH /instances/{id}; POST /refresh (dev-only, gate @Value("${pattern.refresh.dev-endpoint-enabled:false}"), 403 when off).
  userId convention: match an existing controller in this repo.
- `pattern/schedule/PatternRefreshScheduler.java` @Component — @Scheduled(cron=@Value("${pattern.refresh.cron:0 30 3 * * *}"))
  iterate findDistinctUserIds(), try/catch per user. Ensure @EnableScheduling exists (add a tiny @Configuration if not).

### 3. Eval harness + baselines (TEST scope: src/test/java/com/ling/linginnerflow/pattern/eval/)
- GroundTruthLoader.java — parse tierA/*.answerkey.yaml + *.corpus.md and sealed Tier A-H into GTPersona{ id, generatorModel,
  List<GTLabel> truePatterns, List<GTLabel> decoys, List<CorpusRecord> corpus }; GTLabel{patternKey,domain,present,intendedStrength}.
  loadTierA()/loadTierAH(); GUARD: throw if a tuning flag is set while loadTierAH() is called (sealing, V1.2 R5).
- MetricsCalculator.java — precision, recall, F1, hard-negative FP rate; stage-wise recall retention AND through-verifier
  recall retention (V1.2 R40: of truly-present, fraction with valid ≥3/≥1-verbatim chain after verification).
- baseline/ : Baseline interface predict(GTPersona)->Set<PredictedPattern>; B0_Prior (seeded), B1_Lexical (YAML lexical_cues,
  homophily-immune floor — headline "full beats B1" V1.2 R30), B2_SinglePrompt (network gated/@Disabled, test the parser),
  B3_RetrievalNoVerify (retrieval, skip verify; isolates verification value).
- eval/README.md — metric triple Tier A | Tier A-H | Tier B; decision rule R34 (keep iff statistically supported on Tier A
  AND not sign-reversed on Tier A-H).

### 4. Unit tests (src/test/java/com/ling/linginnerflow/pattern/) — keep offline (mock ChatClient/EmbeddingModel), no DB
- PatternDefinitionLoaderTest (12 load, keys/domains valid)
- ConfidenceScorerTest (NO strength term; crafted-chain values; 0 items -> 0)
- EvidenceChainAssemblerTest (<3 rejected; 0 verbatim rejected; single-day rejected; crisis excluded; verbatim exact-substring)
- PatternDeduplicatorTest (mock EmbeddingModel; 0.88 threshold behavior)

### 5. Integration
`cd <worktree> && ./mvnw -q -DskipTests compile` → fix cross-file mismatches WITHIN pattern/ only (align callers to actual
created signatures; add missing repo methods additively; never weaken an invariant test). Then
`./mvnw -q -Dtest='com.ling.linginnerflow.pattern.**' test`. Iterate until pattern package compiles and pattern tests pass.
