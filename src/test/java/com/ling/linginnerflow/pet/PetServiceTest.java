package com.ling.linginnerflow.pet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PetService unit tests.
 *
 * Coverage:
 *   getOrCreate   — creates new / returns existing
 *   addAwareness  — correct gain per emotion level, L3 highest, L5 protected
 *   addVitality   — tap gain capped at 5.0/call, vitality hard-capped at 100
 *   addStability  — fixed +2 gain, growthPoints updated
 *   cohesion      — formula: awareness×0.6 + stability×0.4, capped at 100
 *   level         — boundary values: cohesion 0/20/40/60/80 → level 1-5
 *   color         — emotion 1-5 each maps to the correct hex colour
 *   applyDecay    — vitality × 0.9 each call; no-op when pet not found
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PetServiceTest {

    @Mock private PetRepository petRepository;

    @InjectMocks private PetService petService;

    /** Returns the argument passed to save() so state is visible in assertions. */
    @BeforeEach
    void setUp() {
        when(petRepository.save(any(PetStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getOrCreate ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreate: returns existing pet when found")
    void getOrCreate_existing() {
        PetStatus existing = pet();
        existing.setLevel(3);
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(existing));

        PetStatus result = petService.getOrCreate("u1");

        assertThat(result.getLevel()).isEqualTo(3);
        verify(petRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreate: creates and saves new pet when not found")
    void getOrCreate_createsNew() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.empty());

        petService.getOrCreate("u1");

        verify(petRepository).save(any(PetStatus.class));
    }

    // ── addAwareness ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addAwareness L1: gain = 1.0")
    void addAwareness_L1_gain1() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addAwareness("u1", 1);

        assertThat(result.getAwareness()).isEqualByComparingTo("1.0");
        assertThat(result.getGrowthPoints()).isEqualTo(1L);
    }

    @Test
    @DisplayName("addAwareness L3: gain = 3.5 (highest value)")
    void addAwareness_L3_gain3point5() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addAwareness("u1", 3);

        assertThat(result.getAwareness()).isEqualByComparingTo("3.5");
        assertThat(result.getGrowthPoints()).isEqualTo(3L);
    }

    @Test
    @DisplayName("addAwareness L5 crisis: gain = 1.0 (user is protected, not rewarded heavily)")
    void addAwareness_L5_protectedGain() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addAwareness("u1", 5);

        assertThat(result.getAwareness()).isEqualByComparingTo("1.0");
        assertThat(result.getPrimaryColor()).isEqualTo("#ff6b6b");
    }

    @Test
    @DisplayName("addAwareness: currentEmotion is updated to the passed emotion level")
    void addAwareness_updatesCurrentEmotion() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addAwareness("u1", 4);

        assertThat(result.getCurrentEmotion()).isEqualTo(4);
    }

    // ── addVitality ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("addVitality: gain = tapCount × 0.1")
    void addVitality_normalGain() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addVitality("u1", 10);

        assertThat(result.getVitality()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("addVitality: gain is capped at 5.0 per call regardless of tap count")
    void addVitality_gainCappedAt5() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addVitality("u1", 1000);

        assertThat(result.getVitality()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("addVitality: total vitality is hard-capped at 100")
    void addVitality_totalCappedAt100() {
        PetStatus pet = pet();
        pet.setVitality(new BigDecimal("98.0"));
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet));

        PetStatus result = petService.addVitality("u1", 1000); // would add 5.0

        assertThat(result.getVitality()).isEqualByComparingTo("100");
    }

    // ── addStability ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addStability: always adds exactly 2.0 and 2 growthPoints")
    void addStability_fixedGain() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet()));

        PetStatus result = petService.addStability("u1");

        assertThat(result.getStability()).isEqualByComparingTo("2.0");
        assertThat(result.getGrowthPoints()).isEqualTo(2L);
    }

    // ── cohesion formula ─────────────────────────────────────────────────────

    @Test
    @DisplayName("cohesion = awareness×0.6 + stability×0.4")
    void cohesion_formula() {
        PetStatus pet = pet();
        pet.setAwareness(new BigDecimal("10.0"));
        pet.setStability(new BigDecimal("10.0"));
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet));

        // 10×0.6 + 10×0.4 = 10 cohesion
        PetStatus result = petService.addStability("u1"); // triggers evolveAndSave
        // after addStability: stability = 12, awareness = 10
        // cohesion = 10×0.6 + 12×0.4 = 6 + 4.8 = 10.8 → 11
        assertThat(result.getCohesion()).isEqualTo(11);
    }

    @Test
    @DisplayName("cohesion is hard-capped at 100")
    void cohesion_cappedAt100() {
        PetStatus pet = pet();
        pet.setAwareness(new BigDecimal("200.0"));
        pet.setStability(new BigDecimal("200.0"));
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet));

        PetStatus result = petService.addStability("u1");

        assertThat(result.getCohesion()).isEqualTo(100);
    }

    // ── cohesionToLevel boundaries ────────────────────────────────────────────

    @Test
    @DisplayName("level boundaries: cohesion 0→1, 20→2, 40→3, 60→4, 80→5")
    void cohesionToLevel_boundaries() {
        assertLevel(0,  1, "雾气态");
        assertLevel(19, 1, "just below 成形态");
        assertLevel(20, 2, "成形态");
        assertLevel(39, 2, "just below 稳定态");
        assertLevel(40, 3, "稳定态");
        assertLevel(59, 3, "just below 光环态");
        assertLevel(60, 4, "光环态");
        assertLevel(79, 4, "just below 晶核态");
        assertLevel(80, 5, "晶核态");
        assertLevel(100, 5, "max cohesion");
    }

    // ── emotionToColor ────────────────────────────────────────────────────────

    @Test
    @DisplayName("emotion colors: each level maps to the correct hex")
    void emotionToColor_allLevels() {
        String[][] cases = {
            {"1", "#b8f0e0"},
            {"2", "#b8e0ff"},
            {"3", "#d4b8ff"},
            {"4", "#ffb347"},
            {"5", "#ff6b6b"},
        };

        for (String[] c : cases) {
            int level = Integer.parseInt(c[0]);
            String expectedColor = c[1];

            when(petRepository.findByUserId("color-u" + level))
                    .thenReturn(Optional.of(pet()));

            PetStatus result = petService.addAwareness("color-u" + level, level);

            assertThat(result.getPrimaryColor())
                    .as("emotion level %d", level)
                    .isEqualTo(expectedColor);
        }
    }

    // ── applyDecay ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyDecay: vitality decreases by 10% each call")
    void applyDecay_reducesVitalityBy10Percent() {
        PetStatus pet = pet();
        pet.setVitality(new BigDecimal("100.00"));
        when(petRepository.findByUserId("u1")).thenReturn(Optional.of(pet));

        petService.applyDecay("u1");

        verify(petRepository).save(argThat(p ->
                p.getVitality().compareTo(new BigDecimal("90.00")) == 0));
    }

    @Test
    @DisplayName("applyDecay: no-op when pet does not exist")
    void applyDecay_noPet_noSave() {
        when(petRepository.findByUserId("u1")).thenReturn(Optional.empty());

        petService.applyDecay("u1");

        verify(petRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a fresh default PetStatus (mirrors entity field defaults). */
    private PetStatus pet() {
        PetStatus p = new PetStatus();
        p.setUserId("u1");
        p.setAwareness(BigDecimal.ZERO);
        p.setVitality(BigDecimal.ZERO);
        p.setStability(BigDecimal.ZERO);
        p.setLevel(1);
        p.setCohesion(0);
        p.setGrowthPoints(0L);
        p.setPrimaryColor("#b8f0e0");
        p.setCurrentEmotion(1);
        return p;
    }

    /**
     * Drives cohesion to exactly targetCohesion by adjusting initial awareness.
     *
     * addStability adds 2.0 to stability before evolveAndSave, so the formula
     * run inside evolveAndSave is:
     *   cohesion = awareness×0.6 + (0 + 2.0)×0.4 = awareness×0.6 + 0.8
     * Solving for awareness: awareness = (targetCohesion - 0.8) / 0.6
     */
    private void assertLevel(int targetCohesion, int expectedLevel, String label) {
        PetStatus pet = pet();
        double awarenessNeeded = Math.max(0, (targetCohesion - 0.8) / 0.6);
        pet.setAwareness(BigDecimal.valueOf(awarenessNeeded));

        String uid = "lvl-u-" + targetCohesion;
        pet.setUserId(uid);
        when(petRepository.findByUserId(uid)).thenReturn(Optional.of(pet));

        // addStability adds 0 awareness change, just triggers evolveAndSave
        PetStatus result = petService.addStability(uid);

        assertThat(result.getLevel())
                .as("cohesion=%d (%s)", targetCohesion, label)
                .isEqualTo(expectedLevel);
    }
}
