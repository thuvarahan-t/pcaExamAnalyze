package com.example.pcaExamAnalyze.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExamPwaController {

    @GetMapping(path = "/exam.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Resource> manifest() {
        return revalidatingResource("exam.webmanifest");
    }

    @GetMapping(path = "/exam-sw.js", produces = "text/javascript")
    public ResponseEntity<Resource> serviceWorker() {
        return revalidatingResource("exam-sw.js");
    }

    @GetMapping(path = "/exam-offline.html", produces = "text/html;charset=UTF-8")
    public ResponseEntity<Resource> offlinePage() {
        return revalidatingResource("exam-offline.html");
    }

    private static ResponseEntity<Resource> revalidatingResource(String filename) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(new ClassPathResource("static/" + filename));
    }
}
