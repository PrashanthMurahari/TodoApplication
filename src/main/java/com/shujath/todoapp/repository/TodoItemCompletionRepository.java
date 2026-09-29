package com.shujath.todoapp.repository;

import com.shujath.todoapp.entity.TodoItemCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoItemCompletionRepository extends JpaRepository<TodoItemCompletion, Long> {
    List<TodoItemCompletion> findByTodoItemIdOrderByCompletedAtAscIdAsc(Long itemId);
}
