package com.meetingnotes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingnotes.model.ActionItem;
import com.meetingnotes.model.Meeting;
import com.meetingnotes.repository.ActionItemRepository;
import com.meetingnotes.repository.MeetingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@Service
public class MeetingService {

    private final MeetingRepository meetingRepo;
    private final ActionItemRepository actionRepo;
    private final ClaudeService claudeService;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper mapper = new ObjectMapper();

    public MeetingService(MeetingRepository meetingRepo,
                          ActionItemRepository actionRepo,
                          ClaudeService claudeService,
                          SimpMessagingTemplate ws) {
        this.meetingRepo  = meetingRepo;
        this.actionRepo   = actionRepo;
        this.claudeService = claudeService;
        this.ws           = ws;
    }

    /**
     * Creates a meeting and kicks off async AI processing
     */
    @Transactional
    public Meeting createMeeting(String title, String transcript) {
        Meeting meeting = Meeting.builder()
                .title(title)
                .transcript(transcript)
                .status("PENDING")
                .build();
        meeting = meetingRepo.save(meeting);
        processMeetingAsync(meeting.getId());
        return meeting;
    }

    /**
     * Async AI processing pipeline:
     * PENDING → PROCESSING → DONE (or FAILED)
     * WebSocket pushes status updates to frontend in real time
     */
    @Async
    @Transactional
    public void processMeetingAsync(Long meetingId) {
        Meeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found"));

        try {
            // Step 1 — Mark as processing, notify frontend
            meeting.setStatus("PROCESSING");
            meetingRepo.save(meeting);
            pushStatus(meetingId, "PROCESSING", "Analyzing transcript with AI...");

            // Step 2 — Call Claude API
            String rawJson = claudeService.analyzeMeeting(meeting.getTranscript());
            log.info("Claude response for meeting {}: {}", meetingId, rawJson);

            // Step 3 — Parse Claude's JSON response
            JsonNode root = mapper.readTree(rawJson);

            meeting.setSummary(root.path("summary").asText());
            meeting.setDecisions(root.path("decisions").asText());
            meeting.setFollowUpEmail(root.path("followUpEmail").asText());
            meeting.setStatus("DONE");
            meeting.setProcessedAt(LocalDateTime.now());
            meetingRepo.save(meeting);

            // Step 4 — Save action items
            JsonNode items = root.path("actionItems");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String dueDateStr = item.path("dueDate").asText("null");
                    LocalDate dueDate = null;
                    if (!dueDateStr.equals("null") && !dueDateStr.isBlank()) {
                        try { dueDate = LocalDate.parse(dueDateStr); }
                        catch (Exception e) { log.warn("Could not parse date: {}", dueDateStr); }
                    }

                    ActionItem action = ActionItem.builder()
                            .meeting(meeting)
                            .description(item.path("description").asText())
                            .owner(item.path("owner").asText("Unassigned"))
                            .dueDate(dueDate)
                            .status("TODO")
                            .build();
                    actionRepo.save(action);
                }
            }

            // Step 5 — Notify frontend processing is complete
            pushStatus(meetingId, "DONE", "Analysis complete!");
            log.info("Meeting {} processed successfully", meetingId);

        } catch (Exception e) {
            log.error("Failed to process meeting {}: {}", meetingId, e.getMessage());
            meeting.setStatus("FAILED");
            meetingRepo.save(meeting);
            pushStatus(meetingId, "FAILED", "Processing failed: " + e.getMessage());
        }
    }

    /** Push real-time status update to browser via WebSocket */
    private void pushStatus(Long meetingId, String status, String message) {
        ws.convertAndSend("/topic/meeting/" + meetingId,
                Map.of("meetingId", meetingId, "status", status, "message", message));
    }

    public List<Meeting> getAllMeetings() {
        return meetingRepo.findAllByOrderByCreatedAtDesc();
    }

    public Meeting getMeeting(Long id) {
        return meetingRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found"));
    }

    public List<ActionItem> getActionItems(Long meetingId) {
        return actionRepo.findByMeetingIdOrderByCreatedAtAsc(meetingId);
    }

    @Transactional
    public ActionItem updateActionStatus(Long actionId, String status, String editor) {
        ActionItem item = actionRepo.findById(actionId)
                .orElseThrow(() -> new NoSuchElementException("Action item not found"));

        // Append to audit log
        String history = item.getEditHistory() == null ? "" : item.getEditHistory();
        history += String.format("[%s] %s changed status to %s\n",
                LocalDateTime.now(), editor, status);

        item.setStatus(status);
        item.setEditHistory(history);
        return actionRepo.save(item);
    }

    public List<Meeting> search(String query) {
        return meetingRepo.search(query);
    }
}