package com.shujath.todoapp.mapper;

import com.shujath.todoapp.dto.todoitem.TodoItemCompletionResponse;
import com.shujath.todoapp.entity.TodoItemCompletion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TodoItemCompletionMapper {

    public TodoItemCompletionResponse toResponse(TodoItemCompletion completion) {
        return TodoItemCompletionResponse.builder()
                .id(completion.getId())
                .occurrenceDate(completion.getOccurrenceDate())
                .completedAt(completion.getCompletedAt())
                .build();
    }

    public List<TodoItemCompletionResponse> toResponseList(List<TodoItemCompletion> completions) {
        return completions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
