package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.OfferContentService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OfferFeedbackPhotoController {
    private final OfferContentService content;

    public OfferFeedbackPhotoController(OfferContentService content) {
        this.content = content;
    }

    @GetMapping({"/exam/packages/feedback/{id}/photo", "/exam/packages/feedback/{id}/photo/{index}"})
    public ResponseEntity<byte[]> publishedPhoto(@PathVariable Long id, @PathVariable(required = false) Integer index) {
        return photo(id, index, false);
    }

    @GetMapping({"/exam/admin/packages/feedback/{id}/photo", "/exam/admin/packages/feedback/{id}/photo/{index}"})
    public ResponseEntity<byte[]> teacherPhoto(@PathVariable Long id, @PathVariable(required = false) Integer index) {
        return photo(id, index, true);
    }

    private ResponseEntity<byte[]> photo(Long id, Integer index, boolean teacher) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .contentType(MediaType.IMAGE_JPEG).body(content.feedbackPhoto(id, index == null ? 0 : index, teacher));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> unavailable(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
