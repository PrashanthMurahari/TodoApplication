package com.shujath.todoapp.mapper;

import com.shujath.todoapp.dto.todoitem.TodoItemResponse;
import com.shujath.todoapp.entity.TodoItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TodoItemMapper {

    public TodoItemResponse toResponse(TodoItem item) {
        return TodoItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .status(item.getStatus().name())
                .deadline(item.getDeadline())
                .recurring(item.isRecurring())
                .recurrenceType(
                        item.getRecurrenceType() != null
                                ? item.getRecurrenceType().name()
                                : null
                )
                .scheduledDate(item.getScheduledDate())
                .nextOccurrence(item.getNextOccurrence())
                .build();
    }
    public List<TodoItemResponse> toResponseList(List<TodoItem> items) {
        return items.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
