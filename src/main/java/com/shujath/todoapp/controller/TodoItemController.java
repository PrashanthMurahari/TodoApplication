package com.shujath.todoapp.controller;

import com.shujath.todoapp.dto.todoitem.CreateTodoItemRequest;
import com.shujath.todoapp.dto.todoitem.TodoItemCompletionResponse;
import com.shujath.todoapp.dto.todoitem.TodoItemResponse;
import com.shujath.todoapp.dto.todoitem.UpdateTodoItemRequest;
import com.shujath.todoapp.service.TodoItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lists/{listId}/items")
public class TodoItemController {

    private final TodoItemService todoItemService;

    // Create todo item
    @PostMapping
    public ResponseEntity<TodoItemResponse> createTodoItem(
            @PathVariable Long listId,
            @RequestBody CreateTodoItemRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        TodoItemResponse response =
                todoItemService.createTodoItem(userId, listId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response); // 201
    }

    // Get all items in a list
    @GetMapping
    public ResponseEntity<List<TodoItemResponse>> getAllTodoItems(
            @PathVariable Long listId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        List<TodoItemResponse> response =
                todoItemService.getAllTodoItems(userId, listId);

        return ResponseEntity.ok(response); // 200
    }

    // Get one todo item
    @GetMapping("/{itemId}")
    public ResponseEntity<TodoItemResponse> getTodoItem(
            @PathVariable Long listId,
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        TodoItemResponse response =
                todoItemService.getTodoItem(userId, listId, itemId);

        return ResponseEntity.ok(response); // 200
    }

    // Update todo item
    @PutMapping("/{itemId}")
    public ResponseEntity<TodoItemResponse> updateTodoItem(
            @PathVariable Long listId,
            @PathVariable Long itemId,
            @RequestBody UpdateTodoItemRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        TodoItemResponse response =
                todoItemService.updateTodoItem(userId, listId, itemId, request);

        return ResponseEntity.ok(response); // 200
    }

    // Delete todo item
    @DeleteMapping("/{itemId}")
    public ResponseEntity<TodoItemResponse> deleteTodoItem(
            @PathVariable Long listId,
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        TodoItemResponse response =
                todoItemService.deleteTodoItem(userId, listId, itemId);

        return ResponseEntity.ok(response); // 200
    }

    // Get completion history of a recurring todo item
    @GetMapping("/{itemId}/completions")
    public ResponseEntity<List<TodoItemCompletionResponse>> getTodoItemCompletions(
            @PathVariable Long listId,
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        List<TodoItemCompletionResponse> response =
                todoItemService.getTodoItemCompletions(userId, listId, itemId);

        return ResponseEntity.ok(response); // 200
    }
}
