package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.SyllabusLevel;
import com.example.pcaExamAnalyze.domain.SyllabusUnit;
import com.example.pcaExamAnalyze.repo.SyllabusUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Syllabus units: one competency each, with competency levels holding periods, a content tree and outcomes. */
@Service
public class SyllabusService {

    private static final int MAX_LEVELS = 60;
    private static final int MAX_NODES = 800;
    private static final int MAX_DEPTH = 30;
    private static final int MAX_OUTCOMES = 100;
    private static final int TREE_COLUMN = 20000;
    private static final int OUTCOMES_COLUMN = 8000;

    private final SyllabusUnitRepository units;
    private volatile CachedUnits cachedUnits = new CachedUnits(0, List.of());

    public SyllabusService(SyllabusUnitRepository units) {
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<UnitView> list() {
        long now = System.nanoTime();
        CachedUnits snapshot = cachedUnits;
        if (now < snapshot.expiresAtNanos()) return snapshot.units();
        List<UnitView> loaded = units.findAllByOrderByPositionAscIdAsc().stream()
                .map(SyllabusService::view).toList();
        cachedUnits = new CachedUnits(now + 300_000_000_000L, loaded);
        return loaded;
    }

    /** Current names + valid content paths/outcomes of one unit level, for tagging questions. */
    @Transactional(readOnly = true)
    public java.util.Optional<ResolvedLevel> resolve(Long unitId, Long levelId) {
        if (unitId == null) return java.util.Optional.empty();
        return units.findById(unitId).map(unit -> {
            SyllabusLevel level = levelId == null ? null : unit.getLevels().stream()
                    .filter(l -> l.getId().equals(levelId)).findFirst().orElse(null);
            java.util.Set<String> paths = new java.util.LinkedHashSet<>();
            java.util.Set<String> outcomes = new java.util.LinkedHashSet<>();
            if (level != null) {
                collectPaths(decodeTree(level.getContentTree()), "", paths);
                outcomes.addAll(decodeOutcomes(level.getOutcomes()));
            }
            return new ResolvedLevel(unit.getId(), unit.getName(), unit.getCompetency() == null ? "" : unit.getCompetency(),
                    level == null ? null : level.getId(), level == null ? null : level.getName(), paths, outcomes);
        });
    }

    /** Separator between tree levels in a stored content path. */
    public static final String PATH_SEPARATOR = " › ";

    private static void collectPaths(List<NodeView> nodes, String prefix, java.util.Set<String> out) {
        for (NodeView node : nodes) {
            String path = prefix.isEmpty() ? node.text() : prefix + PATH_SEPARATOR + node.text();
            out.add(path);
            collectPaths(node.children(), path, out);
        }
    }

    @Transactional
    public UnitView create(UnitPayload payload) {
        SyllabusUnit unit = new SyllabusUnit();
        unit.setPosition(units.maxPosition() + 1);
        apply(unit, payload);
        UnitView saved = view(units.save(unit));
        invalidateCache();
        return saved;
    }

    @Transactional
    public UnitView update(Long id, UnitPayload payload) {
        SyllabusUnit unit = units.findById(id).orElseThrow(() -> new IllegalArgumentException("Unit not found"));
        apply(unit, payload);
        UnitView saved = view(units.saveAndFlush(unit));
        invalidateCache();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        units.deleteById(id);
        invalidateCache();
    }

    private void invalidateCache() {
        cachedUnits = new CachedUnits(0, List.of());
    }

    private static void apply(SyllabusUnit unit, UnitPayload payload) {
        if (payload == null) throw new IllegalArgumentException("Nothing to save");
        String name = line(payload.name(), 200);
        if (name.isEmpty()) throw new IllegalArgumentException("Enter the unit name");
        unit.setName(name);
        unit.setCompetency(emptyToNull(line(payload.competency(), 1000)));
        unit.setTotalPeriods(validPeriods(payload.totalPeriods()));
        unit.setUpdatedAt(Instant.now());

        // Update levels in place by id, add new ones, drop the ones no longer sent.
        Map<Long, SyllabusLevel> existing = new HashMap<>();
        unit.getLevels().forEach(level -> existing.put(level.getId(), level));
        List<SyllabusLevel> next = new ArrayList<>();
        List<LevelPayload> levels = payload.levels() == null ? List.of() : payload.levels();
        if (levels.size() > MAX_LEVELS) throw new IllegalArgumentException("A unit can have up to " + MAX_LEVELS + " competency levels");
        for (LevelPayload source : levels) {
            if (source == null) continue;
            SyllabusLevel level = source.id() != null && existing.containsKey(source.id())
                    ? existing.remove(source.id()) : new SyllabusLevel();
            level.setUnit(unit);
            level.setPosition(next.size() + 1);
            String levelName = line(source.name(), 300);
            level.setName(levelName.isEmpty() ? "Competency level " + (next.size() + 1) : levelName);
            level.setPeriods(validPeriods(source.periods()));
            level.setContentTree(emptyToNull(encodeTree(source.contents())));
            level.setOutcomes(emptyToNull(encodeOutcomes(source.outcomes())));
            next.add(level);
        }
        unit.getLevels().clear();
        unit.getLevels().addAll(next);
    }

    // ------------------------------------------------------------------ tree encoding

    static String encodeTree(List<NodePayload> nodes) {
        StringBuilder out = new StringBuilder();
        int[] count = {0};
        appendNodes(out, nodes, 0, count);
        return out.toString();
    }

    private static void appendNodes(StringBuilder out, List<NodePayload> nodes, int depth, int[] count) {
        if (nodes == null || depth > MAX_DEPTH) return;
        for (NodePayload node : nodes) {
            if (node == null) continue;
            String text = line(node.text(), 500);
            List<NodePayload> children = node.children();
            boolean hasChildren = children != null && !children.isEmpty();
            if (text.isEmpty() && !hasChildren) continue;
            if (++count[0] > MAX_NODES) throw new IllegalArgumentException("Content can have up to " + MAX_NODES + " items per level");
            String row = "\t".repeat(depth) + (text.isEmpty() ? "(untitled)" : text);
            if (out.length() + row.length() + 1 > TREE_COLUMN) {
                throw new IllegalArgumentException("This competency level's content is too long to save");
            }
            if (!out.isEmpty()) out.append('\n');
            out.append(row);
            appendNodes(out, children, depth + 1, count);
        }
    }

    static List<NodeView> decodeTree(String stored) {
        List<NodeView> roots = new ArrayList<>();
        if (stored == null || stored.isBlank()) return roots;
        List<List<NodeView>> stack = new ArrayList<>();
        stack.add(roots);
        for (String raw : stored.split("\n")) {
            if (raw.isBlank()) continue;
            int depth = 0;
            while (depth < raw.length() && raw.charAt(depth) == '\t') depth++;
            depth = Math.min(depth, stack.size() - 1);
            NodeView node = new NodeView(raw.substring(depth).trim(), new ArrayList<>());
            stack.get(depth).add(node);
            while (stack.size() > depth + 1) stack.remove(stack.size() - 1);
            stack.add(node.children());
        }
        return roots;
    }

    private static String encodeOutcomes(List<String> outcomes) {
        if (outcomes == null) return "";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String outcome : outcomes) {
            String text = line(outcome, 500);
            if (text.isEmpty()) continue;
            if (++count > MAX_OUTCOMES) break;
            if (out.length() + text.length() + 1 > OUTCOMES_COLUMN) {
                throw new IllegalArgumentException("This competency level's learning outcomes are too long to save");
            }
            if (!out.isEmpty()) out.append('\n');
            out.append(text);
        }
        return out.toString();
    }

    private static List<String> decodeOutcomes(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return java.util.Arrays.stream(stored.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    /** Single-line text: tabs/newlines collapse to spaces so they cannot break the stored format. */
    private static String line(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static Integer validPeriods(Integer value) {
        return value != null && value >= 0 && value <= 999 ? value : null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static UnitView view(SyllabusUnit unit) {
        List<LevelView> levels = unit.getLevels().stream()
                .map(level -> new LevelView(level.getId(), level.getName(), level.getPeriods(),
                        decodeTree(level.getContentTree()), decodeOutcomes(level.getOutcomes())))
                .toList();
        return new UnitView(unit.getId(), unit.getName(), unit.getCompetency() == null ? "" : unit.getCompetency(),
                unit.getTotalPeriods(), levels);
    }

    public record ResolvedLevel(Long unitId, String unitName, String competency, Long levelId, String levelName,
                                java.util.Set<String> contentPaths, java.util.Set<String> outcomes) {}
    public record UnitPayload(String name, String competency, Integer totalPeriods, List<LevelPayload> levels) {}
    public record LevelPayload(Long id, String name, Integer periods, List<NodePayload> contents, List<String> outcomes) {}
    public record NodePayload(String text, List<NodePayload> children) {}

    public record UnitView(Long id, String name, String competency, Integer totalPeriods, List<LevelView> levels) {}
    public record LevelView(Long id, String name, Integer periods, List<NodeView> contents, List<String> outcomes) {}
    public record NodeView(String text, List<NodeView> children) {}
    private record CachedUnits(long expiresAtNanos, List<UnitView> units) {}
}
