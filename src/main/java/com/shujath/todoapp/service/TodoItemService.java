package com.shujath.todoapp.service;

import com.shujath.todoapp.dto.todoitem.CreateTodoItemRequest;
import com.shujath.todoapp.dto.todoitem.TodoItemCompletionResponse;
import com.shujath.todoapp.dto.todoitem.TodoItemResponse;
import com.shujath.todoapp.dto.todoitem.UpdateTodoItemRequest;

import java.util.List;

public interface TodoItemService {
    TodoItemResponse createTodoItem(Long userId, Long listId, CreateTodoItemRequest request);
    List<TodoItemResponse> getAllTodoItems(Long userId, Long listId);
    TodoItemResponse getTodoItem(Long userId, Long listId, Long itemId);
    TodoItemResponse updateTodoItem(
            Long userId,
            Long listId,
            Long itemId,
            UpdateTodoItemRequest request
    );

    TodoItemResponse deleteTodoItem(Long userId, Long listId, Long itemId);

    List<TodoItemCompletionResponse> getTodoItemCompletions(Long userId, Long listId, Long itemId);
}
