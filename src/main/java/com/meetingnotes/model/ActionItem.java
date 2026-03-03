package com.meetingnotes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "action_items")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Who owns this action item
    @Column
    private String owner;

    // Due date extracted by AI
    @Column
    private LocalDate dueDate;

    // TODO → IN_PROGRESS → DONE
    @Column(nullable = false)
    private String status = "TODO";

    // Audit — track edits
    @Column(columnDefinition = "TEXT")
    private String editHistory;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}