package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.SyllabusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** JSON API behind the admin Syllabus tab (page itself is served by McqAdminController). */
@RestController
@RequestMapping("/exam/admin/syllabus/units")
public class SyllabusController {

    private final SyllabusService syllabus;

    public SyllabusController(SyllabusService syllabus) {
        this.syllabus = syllabus;
    }

    @GetMapping
    public List<SyllabusService.UnitView> list() {
        return syllabus.list();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SyllabusService.UnitPayload payload) {
        try {
            return ResponseEntity.ok(syllabus.create(payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SyllabusService.UnitPayload payload) {
        try {
            return ResponseEntity.ok(syllabus.update(id, payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        syllabus.delete(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
