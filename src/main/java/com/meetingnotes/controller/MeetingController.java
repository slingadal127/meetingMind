package com.meetingnotes.controller;

import com.meetingnotes.model.ActionItem;
import com.meetingnotes.model.Meeting;
import com.meetingnotes.service.MeetingService;
import com.meetingnotes.service.TranscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/meetings")
@CrossOrigin(origins = "*")
public class MeetingController {

    private final MeetingService meetingService;
    private final TranscriptionService transcriptionService;

    public MeetingController(MeetingService meetingService,
                             TranscriptionService transcriptionService) {
        this.meetingService       = meetingService;
        this.transcriptionService = transcriptionService;
    }

    /**
     * POST /api/meetings/upload
     * Upload audio or video file → Whisper transcribes → Claude analyzes
     */
    @PostMapping("/upload")
    public ResponseEntity<Meeting> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", defaultValue = "Untitled Meeting") String title) {
        try {
            log.info("Received file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

            // Step 1: Transcribe with Whisper
            String transcript = transcriptionService.transcribe(file);
            if (transcript.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            // Step 2: Create meeting + kick off async Claude analysis
            Meeting meeting = meetingService.createMeeting(title, transcript);
            return ResponseEntity.ok(meeting);

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /** POST /api/meetings — paste transcript directly */
    @PostMapping
    public ResponseEntity<Meeting> create(@RequestBody Map<String, String> body) {
        String title      = body.getOrDefault("title", "Untitled Meeting");
        String transcript = body.get("transcript");
        if (transcript == null || transcript.isBlank())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(meetingService.createMeeting(title, transcript));
    }

    /** GET /api/meetings */
    @GetMapping
    public ResponseEntity<List<Meeting>> list() {
        return ResponseEntity.ok(meetingService.getAllMeetings());
    }

    /** GET /api/meetings/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Meeting> get(@PathVariable Long id) {
        try { return ResponseEntity.ok(meetingService.getMeeting(id)); }
        catch (Exception e) { return ResponseEntity.notFound().build(); }
    }

    /** GET /api/meetings/{id}/actions */
    @GetMapping("/{id}/actions")
    public ResponseEntity<List<ActionItem>> actions(@PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getActionItems(id));
    }

    /** PATCH /api/meetings/actions/{actionId} */
    @PatchMapping("/actions/{actionId}")
    public ResponseEntity<ActionItem> updateAction(
            @PathVariable Long actionId,
            @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(meetingService.updateActionStatus(
                    actionId,
                    body.getOrDefault("status", "TODO"),
                    body.getOrDefault("editor", "User")));
        } catch (Exception e) { return ResponseEntity.notFound().build(); }
    }

    /** GET /api/meetings/search?q=keyword */
    @GetMapping("/search")
    public ResponseEntity<List<Meeting>> search(@RequestParam String q) {
        return ResponseEntity.ok(meetingService.search(q));
    }
}