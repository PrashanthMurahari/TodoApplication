package com.shujath.todoapp.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "todo_items")
@Getter
@Setter
@ToString(exclude = "todoList")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private LocalDate deadline;

    @Column(nullable = false)
    private boolean recurring;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type")
    private RecurrenceType recurrenceType;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "next_occurrence")
    private LocalDate nextOccurrence;

    @Column(name = "recurrence_anchor_date")
    private LocalDate recurrenceAnchorDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "todo_list_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "todo_items_todo_lists_id_fk")
    )
    private TodoList todoList;

    public enum Status {
        CREATED,
        IN_PROGRESS,
        COMPLETED,
        NOT_APPLICABLE
    }

    public enum RecurrenceType {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
