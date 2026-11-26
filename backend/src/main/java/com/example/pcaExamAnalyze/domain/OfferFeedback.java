package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class OfferFeedback {
    public enum Status { PENDING, PUBLISHED, HIDDEN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String studentName;
    @Column(nullable = false, length = 150)
    private String school;
    @Column(nullable = false, length = 40)
    private String district;
    @Column(nullable = false, length = 160)
    private String packageName;
    @Column(nullable = false, length = 600)
    private String comment;
    @Column(nullable = false)
    private Instant receivedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;
    @Column(length = 80)
    private String photoKey;
    private Integer photoBytes;
    private Integer photoWidth;
    private Integer photoHeight;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "offer_feedback_additional_photos", joinColumns = @JoinColumn(name = "feedback_id", nullable = false))
    @OrderColumn(name = "photo_position", nullable = false)
    private List<PhotoMetadata> additionalPhotos = new ArrayList<>();

    public int getPhotoCount() {
        return photoKey == null ? 0 : 1 + additionalPhotos.size();
    }

    public void addPhoto(PhotoMetadata photo) {
        if (photoKey != null) {
            additionalPhotos.add(photo);
            return;
        }
        // The first attachment uses the original columns for existing records and links.
        photoKey = photo.key();
        photoBytes = photo.bytes();
        photoWidth = photo.width();
        photoHeight = photo.height();
    }

    public PhotoMetadata photoAt(int index) {
        if (index < 0 || index >= getPhotoCount()) return null;
        return index == 0 ? new PhotoMetadata(photoKey, photoBytes, photoWidth, photoHeight) : additionalPhotos.get(index - 1);
    }

    @Embeddable
    public record PhotoMetadata(
        @Column(name = "object_key", nullable = false, length = 80) String key,
        @Column(name = "byte_count", nullable = false) Integer bytes,
        @Column(nullable = false) Integer width,
        @Column(nullable = false) Integer height) {}
}
