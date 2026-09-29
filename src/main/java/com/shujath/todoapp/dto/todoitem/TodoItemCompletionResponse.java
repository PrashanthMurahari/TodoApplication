package com.shujath.todoapp.dto.todoitem;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class TodoItemCompletionResponse {
    private Long id;
    private LocalDate occurrenceDate;
    private Instant completedAt;
}
