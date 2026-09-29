package com.shujath.todoapp.service.impl;

import com.shujath.todoapp.dto.todoitem.CreateTodoItemRequest;
import com.shujath.todoapp.dto.todoitem.TodoItemCompletionResponse;
import com.shujath.todoapp.dto.todoitem.TodoItemResponse;
import com.shujath.todoapp.dto.todoitem.UpdateTodoItemRequest;
import com.shujath.todoapp.entity.TodoItem;
import com.shujath.todoapp.entity.TodoItemCompletion;
import com.shujath.todoapp.entity.TodoList;
import com.shujath.todoapp.exception.BadRequestException;
import com.shujath.todoapp.exception.ForbiddenException;
import com.shujath.todoapp.exception.NotFoundException;
import com.shujath.todoapp.mapper.TodoItemCompletionMapper;
import com.shujath.todoapp.mapper.TodoItemMapper;
import com.shujath.todoapp.repository.TodoItemCompletionRepository;
import com.shujath.todoapp.repository.TodoItemRepository;
import com.shujath.todoapp.repository.TodoListRepository;
import com.shujath.todoapp.service.RecurrenceCalculator;
import com.shujath.todoapp.service.TodoItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoItemServiceImpl implements TodoItemService {

    private final TodoItemRepository todoItemRepository;
    private final TodoListRepository todoListRepository;
    private final TodoItemCompletionRepository todoItemCompletionRepository;
    private final TodoItemMapper todoItemMapper;
    private final TodoItemCompletionMapper todoItemCompletionMapper;
    private final RecurrenceCalculator recurrenceCalculator;
    private final Clock clock;

    @Override
    public TodoItemResponse createTodoItem(
            Long userId,
            Long listId,
            CreateTodoItemRequest request
    ) {

        TodoList todoList = todoListRepository.findById(listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo list not found")
                );

        if (!todoList.getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to add items to this list"
            );
        }

        TodoItem todoItem = TodoItem.builder()
                .title(request.getTitle())
                .status(TodoItem.Status.valueOf(request.getStatus()))
                .deadline(request.getDeadline())
                .todoList(todoList)
                .build();

        if (Boolean.TRUE.equals(request.getRecurring())) {

            if (request.getDeadline() != null) {
                throw new BadRequestException(
                        "Deadline is not supported for recurring todo items, use scheduledDate instead"
                );
            }

            if (todoItem.getStatus() == TodoItem.Status.COMPLETED) {
                throw new BadRequestException(
                        "A recurring todo item cannot be created with status COMPLETED"
                );
            }

            TodoItem.RecurrenceType recurrenceType =
                    parseRecurrenceType(request.getRecurrenceType());

            requireRecurrenceDetails(recurrenceType, request.getScheduledDate());

            applyRecurrence(todoItem, recurrenceType, request.getScheduledDate());
        } else {
            rejectRecurrenceDetails(request.getRecurrenceType(), request.getScheduledDate());
        }

        TodoItem saved = todoItemRepository.save(todoItem);

        return todoItemMapper.toResponse(saved);
    }

    @Override
    public List<TodoItemResponse> getAllTodoItems(Long userId, Long listId) {

        TodoList todoList = todoListRepository.findById(listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo list not found")
                );

        if (!todoList.getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to view items of this list"
            );
        }

        List<TodoItem> items = todoItemRepository.findByTodoListId(listId);

        return todoItemMapper.toResponseList(items);
    }

    @Override
    public TodoItemResponse getTodoItem(
            Long userId,
            Long listId,
            Long itemId
    ) {

        TodoItem item = todoItemRepository
                .findByIdAndTodoListId(itemId, listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo item not found for this list")
                );

        if (!item.getTodoList().getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to view this todo item"
            );
        }

        return todoItemMapper.toResponse(item);
    }

    @Override
    @Transactional
    public TodoItemResponse updateTodoItem(
            Long userId,
            Long listId,
            Long itemId,
            UpdateTodoItemRequest request
    ) {

        TodoItem item = todoItemRepository
                .findByIdAndTodoListId(itemId, listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo item not found for this list")
                );

        if (!item.getTodoList().getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to update this todo item"
            );
        }

        boolean wasRecurring = item.isRecurring();
        boolean recurring = request.getRecurring() != null
                ? request.getRecurring()
                : wasRecurring;

        if (recurring) {

            if (request.getDeadline() != null) {
                throw new BadRequestException(
                        "Deadline is not supported for recurring todo items, use scheduledDate instead"
                );
            }

            TodoItem.RecurrenceType requestedType =
                    parseRecurrenceType(request.getRecurrenceType());

            TodoItem.RecurrenceType recurrenceType = requestedType != null
                    ? requestedType
                    : item.getRecurrenceType();

            LocalDate scheduledDate = request.getScheduledDate() != null
                    ? request.getScheduledDate()
                    : item.getScheduledDate();

            requireRecurrenceDetails(recurrenceType, scheduledDate);

            // Only re-anchor the schedule when it actually changes, so re-sending the
            // current values does not reset a monthly anchor (e.g. the 31st)
            if (recurrenceType != item.getRecurrenceType()
                    || !scheduledDate.equals(item.getScheduledDate())) {
                applyRecurrence(item, recurrenceType, scheduledDate);
            }
        } else {
            rejectRecurrenceDetails(request.getRecurrenceType(), request.getScheduledDate());

            if (wasRecurring) {
                clearRecurrence(item);
            }
        }

        if (request.getTitle() != null) {
            item.setTitle(request.getTitle());
        }

        if (request.getStatus() != null) {
            item.setStatus(TodoItem.Status.valueOf(request.getStatus()));
        }

        if (request.getDeadline() != null) {
            item.setDeadline(request.getDeadline());
        }

        // A recurring todo item is never permanently completed
        if (recurring && item.getStatus() == TodoItem.Status.COMPLETED) {
            if (request.getStatus() != null) {
                completeOccurrence(item);
            } else {
                // Completed one-time item converted to recurring starts fresh
                item.setStatus(TodoItem.Status.CREATED);
            }
        }

        TodoItem updated = todoItemRepository.save(item);

        return todoItemMapper.toResponse(updated);
    }

    @Override
    public TodoItemResponse deleteTodoItem(
            Long userId,
            Long listId,
            Long itemId
    ) {

        TodoItem item = todoItemRepository
                .findByIdAndTodoListId(itemId, listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo item not found for this list")
                );

        if (!item.getTodoList().getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to delete this todo item"
            );
        }

        TodoItemResponse response = todoItemMapper.toResponse(item);

        // Completion history is removed by the database (on delete cascade)
        todoItemRepository.delete(item);

        return response;
    }

    @Override
    public List<TodoItemCompletionResponse> getTodoItemCompletions(
            Long userId,
            Long listId,
            Long itemId
    ) {

        TodoItem item = todoItemRepository
                .findByIdAndTodoListId(itemId, listId)
                .orElseThrow(() ->
                        new NotFoundException("Todo item not found for this list")
                );

        if (!item.getTodoList().getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "You are not allowed to view the completion history of this todo item"
            );
        }

        List<TodoItemCompletion> completions =
                todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(itemId);

        return todoItemCompletionMapper.toResponseList(completions);
    }

    // Records the current occurrence as completed and moves the item to its next occurrence
    private void completeOccurrence(TodoItem item) {

        TodoItemCompletion completion = TodoItemCompletion.builder()
                .todoItem(item)
                .occurrenceDate(item.getScheduledDate())
                .completedAt(clock.instant())
                .build();

        todoItemCompletionRepository.save(completion);

        LocalDate nextScheduledDate = item.getNextOccurrence();

        item.setScheduledDate(nextScheduledDate);
        item.setNextOccurrence(
                recurrenceCalculator.nextOccurrence(
                        item.getRecurrenceType(),
                        nextScheduledDate,
                        item.getRecurrenceAnchorDate()
                )
        );
        item.setStatus(TodoItem.Status.CREATED);
    }

    private void applyRecurrence(
            TodoItem item,
            TodoItem.RecurrenceType recurrenceType,
            LocalDate scheduledDate
    ) {
        item.setRecurring(true);
        item.setRecurrenceType(recurrenceType);
        item.setScheduledDate(scheduledDate);
        item.setRecurrenceAnchorDate(scheduledDate);
        item.setNextOccurrence(
                recurrenceCalculator.nextOccurrence(recurrenceType, scheduledDate, scheduledDate)
        );
        item.setDeadline(null);
    }

    private void clearRecurrence(TodoItem item) {
        item.setRecurring(false);
        item.setRecurrenceType(null);
        item.setScheduledDate(null);
        item.setNextOccurrence(null);
        item.setRecurrenceAnchorDate(null);
    }

    private TodoItem.RecurrenceType parseRecurrenceType(String recurrenceType) {

        if (recurrenceType == null) {
            return null;
        }

        try {
            return TodoItem.RecurrenceType.valueOf(recurrenceType);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Unsupported recurrence type '" + recurrenceType + "', allowed values are "
                            + Arrays.toString(TodoItem.RecurrenceType.values())
            );
        }
    }

    private void requireRecurrenceDetails(
            TodoItem.RecurrenceType recurrenceType,
            LocalDate scheduledDate
    ) {

        if (recurrenceType == null) {
            throw new BadRequestException(
                    "Recurrence type is required for recurring todo items"
            );
        }

        if (scheduledDate == null) {
            throw new BadRequestException(
                    "Scheduled date is required for recurring todo items"
            );
        }
    }

    private void rejectRecurrenceDetails(String recurrenceType, LocalDate scheduledDate) {

        if (recurrenceType != null || scheduledDate != null) {
            throw new BadRequestException(
                    "Recurrence type and scheduled date are only allowed for recurring todo items"
            );
        }
    }
}
