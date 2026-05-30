package com.ling.linginnerflow.pattern.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.SourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceChainAssemblerTest {

    private static final String PATTERN_KEY = "pattern.key";
    private static final String PATTERN_INSTANCE_ID = "pattern-instance-1";

    private final EvidenceChainAssembler assembler = new EvidenceChainAssembler();

    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(EvidenceChainAssembler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void happy_path_assembles() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(3), "I wrote down the pattern.", false),
                doc("doc-4", "ref-4", day(3), "I noticed it again later.", false)
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isPresent();
        assertThat(assembled.get().items()).hasSize(4);
        assertThat(assembled.get().items())
                .anySatisfy(item -> {
                    assertThat(item.isVerbatim()).isTrue();
                    assertThat(item.getExcerpt()).isEqualTo("keep stepping back");
                });
        assertThat(assembled.get().chain().getPatternInstanceId()).isEqualTo(PATTERN_INSTANCE_ID);
        assertThat(assembled.get().chain().getId()).isNotBlank();
        assertThat(assembled.get().domain()).isEqualTo(Domain.self);
    }

    @Test
    void fewer_than_three_supports_drops() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false)
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isEmpty();
        assertLoggedDropReason("DROP_INSUFFICIENT_EVIDENCE");
    }

    @Test
    void zero_verbatim_drops() {
        Fixture fixture = fixture(
                nonVerbatimDoc("doc-1", "ref-1", day(1)),
                nonVerbatimDoc("doc-2", "ref-2", day(2)),
                nonVerbatimDoc("doc-3", "ref-3", day(3)),
                nonVerbatimDoc("doc-4", "ref-4", day(4))
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isEmpty();
        assertLoggedDropReason("DROP_NO_VERBATIM");
    }

    @Test
    void single_day_drops() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(1).plusHours(1), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(1).plusHours(2), "I wrote down the pattern.", false),
                doc("doc-4", "ref-4", day(1).plusHours(3), "I noticed it again later.", false)
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isEmpty();
        assertLoggedDropReason("DROP_SINGLE_DAY");
    }

    @Test
    void crisis_flagged_source_drops_whole_chain() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(3), "I wrote down the pattern.", true),
                doc("doc-4", "ref-4", day(4), "I noticed it again later.", false)
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isEmpty();
        assertLoggedDropReason("DROP_CRISIS");
    }

    @Test
    void dedup_by_source_ref_before_count() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(3), "I wrote down the pattern.", false),
                doc("doc-4", "ref-3", day(4), "Duplicate source ref from another chunk.", false)
        );

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isPresent();
        assertThat(assembled.get().items()).hasSize(3);
        assertThat(assembled.get().items())
                .extracting("sourceRef")
                .containsExactly("ref-1", "ref-2", "ref-3");
    }

    @Test
    void verbatim_must_match_source_text_exactly() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(3), "I wrote down the pattern.", false),
                doc("doc-4", "ref-4", day(4), "I noticed it again later.", false)
        );
        fixture.results().forEach(result -> {
            result.setVerbatimQuotable(false);
            result.setVerbatimSpan(null);
        });
        VerificationResult invalidVerbatim = fixture.results().get(0);
        invalidVerbatim.setVerbatimQuotable(true);
        invalidVerbatim.setVerbatimSpan("this text is not in the source doc");
        simulateVerifierSubstringGuard(invalidVerbatim, fixture.docs().get(0));

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.self
        );

        assertThat(assembled).isEmpty();
        assertLoggedDropReason("DROP_NO_VERBATIM");
    }

    @Test
    void majority_vote_domain_with_tiebreak_to_primary() {
        Fixture fixture = fixture(
                doc("doc-1", "ref-1", day(1), "I keep stepping back before answering.", false),
                doc("doc-2", "ref-2", day(2), "I asked for time to think.", false),
                doc("doc-3", "ref-3", day(3), "I wrote down the pattern.", false),
                doc("doc-4", "ref-4", day(4), "I noticed it again later.", false)
        );
        fixture.results().get(0).setInferredDomain(Domain.family);
        fixture.results().get(1).setInferredDomain(Domain.family);
        fixture.results().get(2).setInferredDomain(Domain.self);
        fixture.results().get(3).setInferredDomain(Domain.self);

        Optional<EvidenceChainAssembler.AssembledChain> assembled = assembler.assemble(
                PATTERN_KEY,
                PATTERN_INSTANCE_ID,
                fixture.results(),
                fixture.docs(),
                Domain.family
        );

        assertThat(assembled).isPresent();
        assertThat(assembled.get().domain()).isEqualTo(Domain.family);
    }

    private void assertLoggedDropReason(String reason) {
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel().equals(Level.INFO))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(reason));
    }

    private static Fixture fixture(CorpusDoc... docs) {
        List<CorpusDoc> sourceDocs = List.of(docs);
        List<VerificationResult> results = sourceDocs.stream()
                .map(EvidenceChainAssemblerTest::resultFor)
                .toList();

        return new Fixture(results, sourceDocs);
    }

    private static CorpusDoc nonVerbatimDoc(String docId, String sourceRef, LocalDateTime occurredAt) {
        return doc(docId, sourceRef, occurredAt, "This supports the pattern without a direct quote.", false);
    }

    private static CorpusDoc doc(
            String docId,
            String sourceRef,
            LocalDateTime occurredAt,
            String text,
            boolean crisisFlag) {
        return CorpusDoc.builder()
                .docId(docId)
                .userId("user-1")
                .sourceType(SourceType.chat_message)
                .sourceRef(sourceRef)
                .occurredAt(occurredAt)
                .text(text)
                .role("user")
                .crisisFlag(crisisFlag)
                .build();
    }

    private static VerificationResult resultFor(CorpusDoc doc) {
        VerificationResult result = new VerificationResult();
        result.setDocId(doc.getDocId());
        result.setSupports(true);
        result.setVerbatimQuotable(doc.getText().contains("keep stepping back"));
        result.setVerbatimSpan(result.isVerbatimQuotable() ? "keep stepping back" : null);
        result.setInterpretation("This source supports the neutral pattern description.");
        result.setInferredDomain(Domain.self);
        return result;
    }

    private static void simulateVerifierSubstringGuard(VerificationResult result, CorpusDoc doc) {
        if (result.getVerbatimSpan() != null && !doc.getText().contains(result.getVerbatimSpan())) {
            result.setVerbatimQuotable(false);
            result.setVerbatimSpan(null);
        }
    }

    private static LocalDateTime day(int dayOfMonth) {
        return LocalDateTime.of(2026, 5, dayOfMonth, 9, 0);
    }

    private record Fixture(List<VerificationResult> results, List<CorpusDoc> docs) {
    }
}
