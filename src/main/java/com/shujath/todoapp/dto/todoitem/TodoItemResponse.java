package com.shujath.todoapp.dto.todoitem;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class TodoItemResponse {
    private Long id;
    private String title;
    private String status;
    private LocalDate deadline;
    private boolean recurring;
    private String recurrenceType;
    private LocalDate scheduledDate;
    private LocalDate nextOccurrence;
}
