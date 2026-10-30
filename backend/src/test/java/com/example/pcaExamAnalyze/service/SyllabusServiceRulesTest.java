package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.SyllabusLevel;
import com.example.pcaExamAnalyze.domain.SyllabusUnit;
import com.example.pcaExamAnalyze.repo.SyllabusUnitRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyllabusServiceRulesTest {
    private SyllabusService svc(SyllabusUnitRepository repo) { return new SyllabusService(repo); }

    @Test void contentTreeRoundTripKeepsHierarchyAndNormalizesWhitespace() {
        var nodes = List.of(
                new SyllabusService.NodePayload(" Mechanics ", List.of(
                        new SyllabusService.NodePayload(" Motion  graphs ", List.of(
                                new SyllabusService.NodePayload("Gradient", List.of()))))),
                new SyllabusService.NodePayload("Heat", List.of()));
        String encoded = SyllabusService.encodeTree(nodes);
        assertEquals("Mechanics\n\tMotion graphs\n\t\tGradient\nHeat", encoded);
        var decoded = SyllabusService.decodeTree(encoded);
        assertEquals(2, decoded.size());
        assertEquals("Gradient", decoded.getFirst().children().getFirst().children().getFirst().text());
        assertEquals("Heat", decoded.getLast().text());
        assertTrue(SyllabusService.decodeTree(null).isEmpty());
        assertTrue(SyllabusService.decodeTree(" \t ").isEmpty());
        assertEquals("", SyllabusService.encodeTree(null));
    }

    @Test void emptyNodesAreSkippedAndUntitledParentIsRetainedWithChild() {
        String output = SyllabusService.encodeTree(Arrays.asList(
                null,
                new SyllabusService.NodePayload(" ", null),
                new SyllabusService.NodePayload(" ", List.of(
                        new SyllabusService.NodePayload("Valid", null)))));
        assertEquals("(untitled)\n\tValid", output);
        assertEquals("Valid", SyllabusService.decodeTree(output).getFirst().children().getFirst().text());
    }

    @Test void nodeCountAndEncodedColumnLimitsRejectOversizedPayloads() {
        var rows = IntStream.range(0, 801).mapToObj(i -> new SyllabusService.NodePayload("node" + i, null)).toList();
        assertThrows(IllegalArgumentException.class, () -> SyllabusService.encodeTree(rows));
        var longer = IntStream.range(0, 90)
                .mapToObj(i -> new SyllabusService.NodePayload("A".repeat(490), null)).toList();
        assertThrows(IllegalArgumentException.class, () -> SyllabusService.encodeTree(longer));
    }

    @Test void invalidNamesAndTooManyLevelsAreRejectedBeforePersistence() {
        var repo = mock(SyllabusUnitRepository.class);
        var service = svc(repo);
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
        assertThrows(IllegalArgumentException.class, () ->
                service.create(new SyllabusService.UnitPayload(" ", " ", 3, null)));
        var levels = IntStream.range(0, 61)
                .mapToObj(i -> new SyllabusService.LevelPayload(null, "L" + i, 1, null, null)).toList();
        assertThrows(IllegalArgumentException.class, () ->
                service.create(new SyllabusService.UnitPayload("Motion", null, 3, levels)));
        verify(repo, never()).save(any());
    }

    @Test void creatingUnitNormalizesCompetencyPeriodsLevelsAndOutcomes() {
        var repo = mock(SyllabusUnitRepository.class);
        when(repo.maxPosition()).thenReturn(4);
        when(repo.save(any(SyllabusUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        var service = svc(repo);
        var level = new SyllabusService.LevelPayload(null, "", 1000,
                List.of(new SyllabusService.NodePayload("Motion", null)),
                Arrays.asList("Interpret graphs", "", "  Velocity 	 gradient  "));
        var view = service.create(new SyllabusService.UnitPayload(
                "  Unit 2  ", "  Kinematics\n & Forces ", -1, Arrays.asList(null, level)));
        assertEquals("Unit 2", view.name());
        assertEquals("Kinematics & Forces", view.competency());
        assertNull(view.totalPeriods());
        assertEquals(1, view.levels().size());
        assertEquals("Competency level 1", view.levels().getFirst().name());
        assertNull(view.levels().getFirst().periods());
        assertEquals(List.of("Interpret graphs", "Velocity gradient"), view.levels().getFirst().outcomes());
        var captor = org.mockito.ArgumentCaptor.forClass(SyllabusUnit.class);
        verify(repo).save(captor.capture());
        assertEquals(5, captor.getValue().getPosition());
    }

    @Test void updateReusesKnownLevelAndDropsRemovedLevel() {
        var repo = mock(SyllabusUnitRepository.class);
        var unit = new SyllabusUnit();
        unit.setId(5L);
        unit.setName("Old");
        var keep = new SyllabusLevel();
        keep.setId(10L);
        keep.setName("Known");
        keep.setUnit(unit);
        var discard = new SyllabusLevel();
        discard.setId(11L);
        discard.setName("Discard");
        discard.setUnit(unit);
        unit.getLevels().add(keep);
        unit.getLevels().add(discard);
        when(repo.findById(5L)).thenReturn(Optional.of(unit));
        when(repo.saveAndFlush(unit)).thenReturn(unit);
        var service = svc(repo);
        var updated = service.update(5L, new SyllabusService.UnitPayload("New", null, 20, List.of(
                new SyllabusService.LevelPayload(10L, "Updated", 15, null, null),
                new SyllabusService.LevelPayload(123L, "New level", 5, null, null))));
        assertEquals("New", updated.name());
        assertEquals(2, updated.levels().size());
        assertSame(keep, unit.getLevels().getFirst());
        assertEquals("Updated", keep.getName());
        assertEquals("New level", unit.getLevels().getLast().getName());
        assertFalse(unit.getLevels().contains(discard));
        assertThrows(IllegalArgumentException.class, () -> service.update(-1L,
                new SyllabusService.UnitPayload("Unknown", null, null, null)));
        service.delete(5L);
        verify(repo).deleteById(5L);
    }

    @Test void resolvedPathsIncludeParentsAndLeafLevels() {
        var repo = mock(SyllabusUnitRepository.class);
        var unit = new SyllabusUnit();
        unit.setId(7L);
        unit.setName("Mechanics");
        unit.setCompetency("Motion");
        var level = new SyllabusLevel();
        level.setId(17L);
        level.setUnit(unit);
        level.setName("Velocity");
        level.setContentTree("Graphs\n\tGradient\n\tArea");
        level.setOutcomes("Interpret velocity\nCalculate displacement");
        unit.getLevels().add(level);
        when(repo.findById(7L)).thenReturn(Optional.of(unit));
        var service = svc(repo);
        assertTrue(service.resolve(null, 17L).isEmpty());
        assertTrue(service.resolve(9L, null).isEmpty());
        var resolved = service.resolve(7L, 17L).orElseThrow();
        assertTrue(resolved.contentPaths().contains("Graphs"));
        assertTrue(resolved.contentPaths().contains("Graphs › Gradient"));
        assertTrue(resolved.contentPaths().contains("Graphs › Area"));
        assertEquals(Set.of("Interpret velocity", "Calculate displacement"), resolved.outcomes());
        assertNull(service.resolve(7L, null).orElseThrow().levelId());
        assertTrue(service.resolve(7L, 999L).orElseThrow().contentPaths().isEmpty());
    }

    @Test void listingUsesCacheAndInvalidatesAfterDelete() {
        var repo = mock(SyllabusUnitRepository.class);
        var u = new SyllabusUnit();
        u.setId(1L);
        u.setName("Mechanics");
        when(repo.findAllByOrderByPositionAscIdAsc()).thenReturn(List.of(u));
        var service = svc(repo);
        assertEquals(1, service.list().size());
        assertEquals(1, service.list().size());
        verify(repo, times(1)).findAllByOrderByPositionAscIdAsc();
        service.delete(1L);
        service.list();
        verify(repo, times(2)).findAllByOrderByPositionAscIdAsc();
    }
}
