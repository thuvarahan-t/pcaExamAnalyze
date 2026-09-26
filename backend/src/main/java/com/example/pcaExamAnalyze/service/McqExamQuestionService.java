package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.repo.McqExamQuestionRepository;
import com.example.pcaExamAnalyze.repo.McqQuestionMeta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

/** Stores and serves the per-question images and metadata of image-sheet MCQ exams. */
@Service
public class McqExamQuestionService {

    static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final McqExamQuestionRepository questions;
    private final R2ImageStorage storage;
    private final com.example.pcaExamAnalyze.repo.McqQuestionTagRepository tags;
    private final SyllabusService syllabus;
    private final com.example.pcaExamAnalyze.repo.McqSubImageRepository subImages;

    public McqExamQuestionService(McqExamQuestionRepository questions, R2ImageStorage storage,
                                  com.example.pcaExamAnalyze.repo.McqQuestionTagRepository tags,
                                  SyllabusService syllabus, com.example.pcaExamAnalyze.repo.McqSubImageRepository subImages) {
        this.questions = questions;
        this.storage = storage;
        this.tags = tags;
        this.syllabus = syllabus;
        this.subImages = subImages;
    }

    /** Question number → its syllabus tags, in order. */
    @Transactional(readOnly = true)
    public Map<Integer, List<TagView>> tagsByExam(Long examId) {
        Map<Integer, List<TagView>> result = new TreeMap<>();
        if (examId == null) return result;
        for (com.example.pcaExamAnalyze.domain.McqQuestionTag tag : tags.findByExamId(examId)) {
            result.computeIfAbsent(tag.getQuestion().getQuestionNumber(), q -> new java.util.ArrayList<>()).add(view(tag));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TagView> tagsOf(Long questionId) {
        return tags.findByQuestionId(questionId).stream().map(McqExamQuestionService::view).toList();
    }

    /**
     * Replaces a question's tags. Each tag is checked against the current syllabus: unknown units
     * are dropped, and only content paths / outcomes that exist in the chosen level are kept.
     */
    private void replaceTags(McqExamQuestion row, List<TagInput> inputs) {
        tags.deleteByQuestionId(row.getId());
        tags.flush();
        if (inputs == null) return;
        int position = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TagInput input : inputs) {
            if (input == null || input.unitId() == null || position >= MAX_TAGS) continue;
            var resolved = syllabus.resolve(input.unitId(), input.levelId()).orElse(null);
            if (resolved == null) continue;
            if (!seen.add(resolved.unitId() + ":" + resolved.levelId())) continue; // same unit+level twice
            com.example.pcaExamAnalyze.domain.McqQuestionTag tag = new com.example.pcaExamAnalyze.domain.McqQuestionTag();
            tag.setQuestion(row);
            tag.setPosition(++position);
            tag.setUnitId(resolved.unitId());
            tag.setUnitName(cut(resolved.unitName(), 200));
            tag.setCompetency(cut(resolved.competency(), 1000));
            tag.setLevelId(resolved.levelId());
            tag.setLevelName(cut(resolved.levelName(), 300));
            tag.setContentPaths(joinKept(input.contents(), resolved.contentPaths()));
            tag.setOutcomes(joinKept(input.outcomes(), resolved.outcomes()));
            tags.save(tag);
        }
    }

    private static final int MAX_TAGS = 10;

    private static String joinKept(List<String> picked, java.util.Set<String> allowed) {
        if (picked == null || picked.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        java.util.Set<String> added = new java.util.HashSet<>();
        for (String value : picked) {
            String v = value == null ? "" : value.trim();
            if (v.isEmpty() || !allowed.contains(v) || !added.add(v)) continue;
            if (out.length() + v.length() + 1 > 8000) break;
            if (!out.isEmpty()) out.append('\n');
            out.append(v);
        }
        return out.isEmpty() ? null : out.toString();
    }

    private static String cut(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static TagView view(com.example.pcaExamAnalyze.domain.McqQuestionTag tag) {
        return new TagView(tag.getUnitId(), tag.getLevelId(), tag.getUnitName() == null ? "" : tag.getUnitName(),
                tag.getCompetency() == null ? "" : tag.getCompetency(), tag.getLevelName() == null ? "" : tag.getLevelName(),
                McqExamQuestion.splitList(tag.getContentPaths()), McqExamQuestion.splitList(tag.getOutcomes()));
    }

    /** Question number → metadata (no image bytes). */
    @Transactional(readOnly = true)
    public Map<Integer, McqQuestionMeta> meta(Long examId) {
        Map<Integer, McqQuestionMeta> result = new TreeMap<>();
        if (examId == null) return result;
        questions.findMetaByExamId(examId).forEach(meta -> result.put(meta.questionNumber(), meta));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Integer> missingImages(Long examId, int totalQuestions) {
        Map<Integer, McqQuestionMeta> meta = meta(examId);
        return IntStream.rangeClosed(1, totalQuestions)
                .filter(q -> meta.get(q) == null || !meta.get(q).hasImage())
                .boxed().toList();
    }

    @Transactional(readOnly = true)
    public Optional<McqExamQuestion> findImage(Long examId, int question) {
        return questions.findByExamIdAndQuestionNumber(examId, question).filter(McqExamQuestion::hasImage);
    }

    /** Returns an error message for an unacceptable upload, or null when it is fine. */
    public static String problem(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) return "Upload a PNG, JPG, WEBP or GIF image";
        if (file.getSize() > McqExamQuestion.MAX_IMAGE_BYTES) return "Image must be 5 MB or smaller";
        return null;
    }

    @Transactional
    public McqExamQuestion save(McqExam exam, int question, QuestionEdit edit, MultipartFile image,
                                boolean removeImage) {
        String problem = problem(image);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (question < 1 || question > exam.getTotalQuestions()) throw new IllegalArgumentException("Invalid question");
        McqExamQuestion row = questions.findByExamIdAndQuestionNumber(exam.getId(), question)
                .orElseGet(McqExamQuestion::new);
        row.setExam(exam);
        row.setQuestionNumber(question);
        row.setWeight(McqExamQuestion.normalizeWeight(edit.weight()));
        row.setTimeSeconds(edit.timeSeconds() != null && McqExamQuestion.TIME_OPTIONS.contains(edit.timeSeconds())
                ? edit.timeSeconds() : null);
        // Older free-text fields: only touched when the form still sends them (null = keep).
        if (edit.units() != null) row.setUnits(McqExamQuestion.joinList(edit.units(), 150, 2000));
        if (edit.competencyLevels() != null) row.setCompetencyLevels(McqExamQuestion.joinList(edit.competencyLevels(), 150, 2000));
        if (edit.contents() != null) row.setContents(McqExamQuestion.joinList(edit.contents(), 400, 4000));
        if (edit.learningOutcomes() != null) row.setLearningOutcomes(McqExamQuestion.joinList(edit.learningOutcomes(), 400, 4000));
        String oldKey = row.getStorageKey();
        if (image != null && !image.isEmpty()) {
            byte[] bytes;
            try {
                bytes = image.getBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            String type = image.getContentType().toLowerCase(Locale.ROOT);
            if (storage.enabled()) {
                String key = objectKey(exam.getId(), question, type);
                storage.put(key, bytes, type);
                row.setStorageKey(key);
                row.setData(null);
            } else {
                row.setStorageKey(null);
                row.setData(bytes);
            }
            row.setContentType(type);
            row.setImageUpdatedAt(Instant.now());
        } else if (removeImage) {
            row.setStorageKey(null);
            row.setData(null);
            row.setContentType(null);
            row.setImageUpdatedAt(null);
        }
        if (oldKey != null && !oldKey.equals(row.getStorageKey())) storage.deleteQuietly(oldKey);
        row.setUpdatedAt(Instant.now());
        McqExamQuestion saved = questions.save(row);
        if (edit.tags() != null) replaceTags(saved, edit.tags());
        return saved;
    }

    @Transactional
    public void deleteForExam(Long examId) {
        subImages.findByExamId(examId).forEach(image -> {
            storage.deleteQuietly(image.getStorageKey());
            subImages.delete(image);
        });
        List<String> keys = questions.findStorageKeysByExamId(examId);
        tags.deleteByExamId(examId);
        questions.deleteByExamId(examId);
        keys.forEach(storage::deleteQuietly);
    }

    /** Where the browser should load a stored image from: an R2 presigned link, or null for DB bytes. */
    public String imageRedirectUrl(McqExamQuestion question) {
        if (question.getStorageKey() == null || !storage.enabled()) return null;
        return storage.presignedUrl(question.getStorageKey(), IMAGE_LINK_VALIDITY);
    }

    /** Moves one database-stored image to R2 (startup migration). Returns true when moved. */
    @Transactional
    public boolean moveImageToStorage(Long id) {
        if (!storage.enabled()) return false;
        McqExamQuestion row = questions.findById(id).orElse(null);
        if (row == null || row.getStorageKey() != null || row.getData() == null || row.getData().length == 0) return false;
        String type = row.getContentType() == null ? "image/png" : row.getContentType();
        String key = objectKey(row.getExam().getId(), row.getQuestionNumber(), type);
        storage.put(key, row.getData(), type);
        row.setStorageKey(key);
        row.setData(null);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> idsWithDatabaseImages() {
        return storage.enabled() ? questions.findIdsWithDatabaseImages() : List.of();
    }

    /**
     * Direct R2 links for every image of an exam, signed once when the page is built, so the
     * browser loads images straight from Cloudflare without a server round-trip per image.
     * Empty maps when R2 is off (images then come from the app's own endpoints).
     */
    @Transactional(readOnly = true)
    public ImageLinks directLinks(Long examId, java.time.Duration validity) {
        if (!storage.enabled() || examId == null) return new ImageLinks(Map.of(), Map.of());
        return links(meta(examId), subImageRefs(examId), validity);
    }

    /** Same as {@link #directLinks} from data the caller already loaded (no extra queries). */
    public ImageLinks links(Map<Integer, McqQuestionMeta> meta,
                            List<com.example.pcaExamAnalyze.repo.McqSubImageRepository.Reference> refs,
                            java.time.Duration validity) {
        if (!storage.enabled()) return new ImageLinks(Map.of(), Map.of());
        Map<Integer, String> main = new TreeMap<>();
        meta.forEach((q, m) -> { if (m.storageKey() != null) main.put(q, storage.presignedUrl(m.storageKey(), validity)); });
        Map<Long, String> sub = new java.util.HashMap<>();
        refs.forEach(ref -> { if (ref.getStorageKey() != null) sub.put(ref.getId(), storage.presignedUrl(ref.getStorageKey(), validity)); });
        return new ImageLinks(main, sub);
    }

    @Transactional(readOnly = true)
    public List<com.example.pcaExamAnalyze.repo.McqSubImageRepository.Reference> subImageRefs(Long examId) {
        return examId == null ? List.of() : subImages.references(examId);
    }

    public static Map<Integer, List<Long>> subImageIds(List<com.example.pcaExamAnalyze.repo.McqSubImageRepository.Reference> refs) {
        Map<Integer, List<Long>> result = new TreeMap<>();
        refs.forEach(ref -> result.computeIfAbsent(ref.getQuestionNumber(), q -> new java.util.ArrayList<>()).add(ref.getId()));
        return result;
    }

    public record ImageLinks(Map<Integer, String> main, Map<Long, String> sub) {}

    /** Presigned image links stay valid this long; the 302 to them is cached for less. */
    public static final java.time.Duration IMAGE_LINK_VALIDITY = java.time.Duration.ofMinutes(30);

    @Transactional(readOnly = true)
    public Map<Integer, List<Long>> subImageIds(Long examId) {
        Map<Integer, List<Long>> result = new TreeMap<>();
        subImages.references(examId).forEach(ref -> result.computeIfAbsent(ref.getQuestionNumber(), q -> new java.util.ArrayList<>()).add(ref.getId()));
        return result;
    }

    @Transactional
    public void saveSubImages(Long examId, int question, List<MultipartFile> files, List<Long> removed) {
        var existing = subImages.findByExamIdAndQuestionNumberOrderById(examId, question);
        var uploads = files == null ? List.<MultipartFile>of() : files.stream().filter(f -> !f.isEmpty()).toList();
        var deletions = removed == null ? List.<Long>of() : removed;
        if (existing.stream().filter(s -> !deletions.contains(s.getId())).count() + uploads.size() > 4)
            throw new IllegalArgumentException("Each question can have up to four supporting images");
        for (var file : uploads) {
            String error = problem(file);
            if (error != null) throw new IllegalArgumentException(error);
        }
        for (var image : existing) if (deletions.contains(image.getId())) {
            subImages.delete(image);
            storage.deleteQuietly(image.getStorageKey());
        }
        for (var file : uploads) {
            var image = new com.example.pcaExamAnalyze.domain.McqSubImage();
            image.setExamId(examId);
            image.setQuestionNumber(question);
            image.setContentType(file.getContentType().toLowerCase(Locale.ROOT));
            try {
                if (storage.enabled()) {
                    String key = objectKey(examId, question, image.getContentType());
                    storage.put(key, file.getBytes(), image.getContentType());
                    image.setStorageKey(key);
                } else image.setData(file.getBytes());
            } catch (IOException ex) { throw new UncheckedIOException(ex); }
            subImages.save(image);
        }
    }

    @Transactional(readOnly = true)
    public Optional<com.example.pcaExamAnalyze.domain.McqSubImage> subImage(Long examId, int question, Long id) {
        return subImages.findByIdAndExamIdAndQuestionNumber(id, examId, question);
    }

    public String subImageRedirectUrl(com.example.pcaExamAnalyze.domain.McqSubImage image) {
        return image.getStorageKey() != null && storage.enabled() ? storage.presignedUrl(image.getStorageKey(), IMAGE_LINK_VALIDITY) : null;
    }

    private static String objectKey(Long examId, int question, String contentType) {
        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
        return "mcq/exam-" + examId + "/q" + question + "-" + java.util.UUID.randomUUID() + "." + ext;
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    /** units are shown to teachers as "Competency". */
    public record QuestionEdit(Double weight, Integer timeSeconds, List<String> units, List<String> competencyLevels,
                               List<String> contents, List<String> learningOutcomes, List<TagInput> tags) {}

    /** One "unit block" from the editor: chosen unit + level and the ticked content paths / outcomes. */
    public record TagInput(Long unitId, Long levelId, List<String> contents, List<String> outcomes) {}

    public record TagView(Long unitId, Long levelId, String unitName, String competency, String levelName,
                          List<String> contents, List<String> outcomes) {}
}
