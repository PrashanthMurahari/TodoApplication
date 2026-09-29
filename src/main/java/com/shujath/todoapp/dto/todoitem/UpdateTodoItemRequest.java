package com.shujath.todoapp.dto.todoitem;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateTodoItemRequest {
    private String title;
    private String status;
    private LocalDate deadline;
    private Boolean recurring;
    private String recurrenceType;
    private LocalDate scheduledDate;
}
