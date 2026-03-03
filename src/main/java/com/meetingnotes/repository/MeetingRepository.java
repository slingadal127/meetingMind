package com.meetingnotes.repository;
import com.meetingnotes.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    List<Meeting> findAllByOrderByCreatedAtDesc();

    @Query("SELECT m FROM Meeting m WHERE " +
            "LOWER(m.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(m.summary) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(m.transcript) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Meeting> search(String q);
}