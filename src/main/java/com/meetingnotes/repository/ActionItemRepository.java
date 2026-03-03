package com.meetingnotes.repository;

import com.meetingnotes.model.ActionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long> {
    List<ActionItem> findByMeetingIdOrderByCreatedAtAsc(Long meetingId);
    List<ActionItem> findByOwnerIgnoreCaseOrderByDueDateAsc(String owner);
}