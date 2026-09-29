package com.shujath.todoapp.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "todo_item_completions")
@Getter
@Setter
@ToString(exclude = "todoItem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoItemCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "todo_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "todo_item_completions_todo_items_id_fk")
    )
    private TodoItem todoItem;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
