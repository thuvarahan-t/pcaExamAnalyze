package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.OfferEvent;
import com.example.pcaExamAnalyze.domain.OfferEventPhoto;
import com.example.pcaExamAnalyze.domain.OfferFeedback;
import com.example.pcaExamAnalyze.domain.OfferFeedback.Status;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class OfferContentService {
    public static final int MAX_EVENT_PHOTOS = 20;
    private static final int PHOTO_CACHE_ENTRIES = 48;
    private static final Logger log = LoggerFactory.getLogger(OfferContentService.class);
    private final EntityManager db;
    private final Map<Long, EventPhotoRenditions> webPhotos = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, EventPhotoRenditions> oldest) {
                return size() > PHOTO_CACHE_ENTRIES;
            }
        });

    public OfferContentService(EntityManager db) {
        this.db = db;
    }

    public record EventCard(OfferEvent event, List<Long> photos) {}
    public record FeedbackInput(String name, String school, String district, String packageName, String comment) {}
    public record FeedbackImportRow(int number, FeedbackInput input) {}
    public record WebPhoto(byte[] data, String contentType) {}
    public enum PhotoVariant { HERO, THUMB }

    public List<EventCard> events(boolean admin) {
        var events = db.createQuery("from OfferEvent e where :admin = true or e.published = true order by e.id desc",
            OfferEvent.class).setParameter("admin", admin).getResultList();
        if (events.isEmpty()) return List.of();
        var photoIds = db.createQuery("""
            select p.event.id, p.id from OfferEventPhoto p where p.event.id in :eventIds order by p.id
            """, Object[].class).setParameter("eventIds", events.stream().map(OfferEvent::getId).toList())
            .getResultList().stream().collect(Collectors.groupingBy(row -> (Long) row[0],
                Collectors.mapping(row -> (Long) row[1], Collectors.toList())));
        return events.stream()
            .map(event -> new EventCard(event, photoIds.getOrDefault(event.getId(), List.of())))
            .toList();
    }

    @Transactional
    public void saveEvent(Long id, String title, String venue, String description, List<MultipartFile> uploads) {
        var event = id == null ? new OfferEvent() :
            required(db.find(OfferEvent.class, id, LockModeType.PESSIMISTIC_WRITE));
        var files = uploads == null ? List.<MultipartFile>of() : uploads.stream().filter(file -> !file.isEmpty()).toList();
        long existingPhotos = id == null ? 0 : eventPhotoCount(id);
        if (existingPhotos + files.size() > MAX_EVENT_PHOTOS)
            throw new IllegalArgumentException("An event can have up to " + MAX_EVENT_PHOTOS + " photos.");
        event.setTitle(optionalText(title, 140, "event name"));
        event.setVenue(optionalText(venue, 140, "venue"));
        event.setDescription(optionalText(description, 480, "description"));
        if (!hasEventText(event) && existingPhotos == 0 && files.isEmpty())
            throw new IllegalArgumentException("Add some event text or a photo.");
        if (id == null) {
            event.setPublished(true);
            db.persist(event);
        }
        for (var file : files) {
            var image = ClassPackageService.validatePoster(file);
            var photo = new OfferEventPhoto();
            photo.setEvent(event);
            photo.setData(image.getData());
            photo.setContentType(image.getContentType());
            db.persist(photo);
        }
    }

    @Transactional
    public void removeEventPhoto(Long eventId, Long photoId) {
        var event = required(db.find(OfferEvent.class, eventId, LockModeType.PESSIMISTIC_WRITE));
        int removed = db.createQuery("delete from OfferEventPhoto p where p.event.id = :eventId and p.id = :photoId")
            .setParameter("eventId", eventId).setParameter("photoId", photoId).executeUpdate();
        if (removed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!hasEventText(event) && eventPhotoCount(eventId) == 0)
            throw new IllegalArgumentException("Add event text or another photo before removing the only photo.");
    }

    private long eventPhotoCount(Long eventId) {
        return db.createQuery("select count(p) from OfferEventPhoto p where p.event.id = :id", Long.class)
            .setParameter("id", eventId).getSingleResult();
    }

    private static boolean hasEventText(OfferEvent event) {
        return !event.getTitle().isEmpty() || !event.getVenue().isEmpty() || !event.getDescription().isEmpty();
    }

    @Transactional
    public void setEventPublished(Long id, boolean published) {
        required(db.find(OfferEvent.class, id, LockModeType.PESSIMISTIC_WRITE)).setPublished(published);
    }

    public OfferEventPhoto photo(Long id, boolean admin) {
        return db.createQuery("""
                select p from OfferEventPhoto p join p.event e
                where p.id = :id and (:admin = true or e.published = true)
                """, OfferEventPhoto.class).setParameter("id", id).setParameter("admin", admin)
            .getResultStream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void requirePublishedPhoto(Long id) {
        boolean available = !db.createQuery("""
            select p.id from OfferEventPhoto p where p.id = :id and p.event.published = true
            """, Long.class).setParameter("id", id).getResultList().isEmpty();
        if (!available) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public WebPhoto webPhoto(Long id, PhotoVariant variant) {
        var cached = webPhotos.get(id);
        if (cached != null) {
            requirePublishedPhoto(id);
        } else {
            var original = photo(id, false);
            try {
                cached = EventPhotoRenditions.create(original.getData());
                webPhotos.put(id, cached);
            } catch (IOException | RuntimeException exception) {
                log.warn("Unable to optimize event photo {}", id, exception);
                return new WebPhoto(original.getData(), original.getContentType());
            }
        }
        return new WebPhoto(variant == PhotoVariant.THUMB ? cached.thumb() : cached.hero(), "image/jpeg");
    }

    public List<OfferFeedback> feedback(boolean admin) {
        var query = db.createQuery("""
            from OfferFeedback f where (:admin = true or f.status = :published)
            order by case when f.status = :pending then 0 else 1 end, f.receivedAt desc, f.id desc
            """, OfferFeedback.class).setParameter("admin", admin).setParameter("published", Status.PUBLISHED)
            .setParameter("pending", Status.PENDING);
        return query.getResultList();
    }

    @Transactional
    public void addFeedback(FeedbackInput input, Status status) {
        db.persist(newFeedback(input, status));
    }

    @Transactional
    public int importFeedback(List<FeedbackImportRow> rows, Status status) {
        for (var row : rows) {
            try {
                db.persist(newFeedback(row.input(), status));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + row.number() + ": " + e.getMessage(), e);
            }
        }
        return rows.size();
    }

    @Transactional
    public void moderate(Long id, Status status) {
        required(db.find(OfferFeedback.class, id)).setStatus(status);
    }

    @Transactional
    public void saveVideo(String value) {
        db.createQuery("update PackageCampaign c set c.youtubeVideoId = :id where c.id = 1")
            .setParameter("id", youtubeId(value)).executeUpdate();
    }

    private static OfferFeedback newFeedback(FeedbackInput input, Status status) {
        var feedback = new OfferFeedback();
        feedback.setStudentName(ClassPackageService.studentName(input.name()));
        feedback.setSchool(requiredText(input.school(), 150, "school"));
        feedback.setDistrict(ClassPackageService.district(requiredText(input.district(), 40, "district")));
        feedback.setPackageName(input.packageName().isBlank() ? "" : requiredText(input.packageName(), 160, "package"));
        feedback.setComment(requiredText(input.comment(), 600, "feedback"));
        feedback.setReceivedAt(Instant.now());
        feedback.setStatus(status);
        return feedback;
    }

    public static String youtubeId(String value) {
        if (value == null || value.isBlank()) return null;
        String id = value.strip();
        if (id.matches("[A-Za-z0-9_-]{11}")) return id;
        try {
            var uri = URI.create(id);
            String host = uri.getHost();
            String path = uri.getPath();
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && host != null) {
                if ("youtu.be".equalsIgnoreCase(host) && path.matches("/[A-Za-z0-9_-]{11}"))
                    return path.substring(1);
                if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com").contains(host.toLowerCase(java.util.Locale.ROOT))) {
                    if (path.matches("/(embed|shorts|live)/[A-Za-z0-9_-]{11}")) return path.substring(path.lastIndexOf('/') + 1);
                    if ("/watch".equals(path) && uri.getRawQuery() != null) {
                        for (String param : uri.getRawQuery().split("&"))
                            if (param.matches("v=[A-Za-z0-9_-]{11}")) return param.substring(2);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URLs use the same validation message as unsupported links.
        }
        throw new IllegalArgumentException("Enter a YouTube video link or its 11-character video ID.");
    }

    private static String requiredText(String value, int max, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max || text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException("Enter " + field + " (up to " + max + " characters).");
        return text;
    }

    private static String optionalText(String value, int max, String field) {
        return value == null || value.isBlank() ? "" : requiredText(value, max, field);
    }

    private static <T> T required(T value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return value;
    }
}
