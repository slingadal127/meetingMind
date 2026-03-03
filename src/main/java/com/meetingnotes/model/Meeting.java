package com.meetingnotes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "meetings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // RAW transcript uploaded by user
    @Column(columnDefinition = "TEXT")
    private String transcript;

    // AI-generated bullet summary
    @Column(columnDefinition = "TEXT")
    private String summary;

    // AI-generated decisions made in meeting
    @Column(columnDefinition = "TEXT")
    private String decisions;

    // AI-generated follow-up email draft
    @Column(columnDefinition = "TEXT")
    private String followUpEmail;

    // PENDING → PROCESSING → DONE → FAILED
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() { createdAt = LocalDateTime.now(); }
}