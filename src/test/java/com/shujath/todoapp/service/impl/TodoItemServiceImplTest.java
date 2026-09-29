package com.shujath.todoapp.service.impl;

import com.shujath.todoapp.dto.todoitem.CreateTodoItemRequest;
import com.shujath.todoapp.dto.todoitem.TodoItemCompletionResponse;
import com.shujath.todoapp.dto.todoitem.TodoItemResponse;
import com.shujath.todoapp.dto.todoitem.UpdateTodoItemRequest;
import com.shujath.todoapp.entity.TodoItem;
import com.shujath.todoapp.entity.TodoItem.RecurrenceType;
import com.shujath.todoapp.entity.TodoItem.Status;
import com.shujath.todoapp.entity.TodoItemCompletion;
import com.shujath.todoapp.entity.TodoList;
import com.shujath.todoapp.entity.User;
import com.shujath.todoapp.exception.BadRequestException;
import com.shujath.todoapp.exception.ForbiddenException;
import com.shujath.todoapp.exception.NotFoundException;
import com.shujath.todoapp.mapper.TodoItemCompletionMapper;
import com.shujath.todoapp.mapper.TodoItemMapper;
import com.shujath.todoapp.repository.TodoItemCompletionRepository;
import com.shujath.todoapp.repository.TodoItemRepository;
import com.shujath.todoapp.repository.TodoListRepository;
import com.shujath.todoapp.service.RecurrenceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoItemServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long LIST_ID = 10L;
    private static final Long ITEM_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-03-10T08:15:30Z");

    @Mock
    private TodoItemRepository todoItemRepository;

    @Mock
    private TodoListRepository todoListRepository;

    @Mock
    private TodoItemCompletionRepository todoItemCompletionRepository;

    private TodoItemServiceImpl service;
    private TodoList ownersList;

    @BeforeEach
    void setUp() {
        service = new TodoItemServiceImpl(
                todoItemRepository,
                todoListRepository,
                todoItemCompletionRepository,
                new TodoItemMapper(),
                new TodoItemCompletionMapper(),
                new RecurrenceCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ownersList = TodoList.builder()
                .id(LIST_ID)
                .name("Work")
                .user(User.builder().id(OWNER_ID).build())
                .build();

        lenient().when(todoItemRepository.save(any(TodoItem.class))).then(returnsFirstArg());
        lenient().when(todoItemCompletionRepository.save(any(TodoItemCompletion.class))).then(returnsFirstArg());
    }

    // ---------------------------------------------------------------- helpers

    private CreateTodoItemRequest createRequest(
            Boolean recurring,
            String recurrenceType,
            LocalDate scheduledDate
    ) {
        CreateTodoItemRequest request = new CreateTodoItemRequest();
        request.setTitle("Water plants");
        request.setStatus("CREATED");
        request.setRecurring(recurring);
        request.setRecurrenceType(recurrenceType);
        request.setScheduledDate(scheduledDate);
        return request;
    }

    private TodoItem oneTimeItem() {
        return TodoItem.builder()
                .id(ITEM_ID)
                .title("Buy milk")
                .status(Status.CREATED)
                .deadline(LocalDate.of(2026, 1, 10))
                .todoList(ownersList)
                .build();
    }

    private TodoItem recurringItem(RecurrenceType type, LocalDate scheduledDate, LocalDate nextOccurrence) {
        return recurringItem(type, scheduledDate, nextOccurrence, scheduledDate);
    }

    private TodoItem recurringItem(
            RecurrenceType type,
            LocalDate scheduledDate,
            LocalDate nextOccurrence,
            LocalDate anchor
    ) {
        return TodoItem.builder()
                .id(ITEM_ID)
                .title("Water plants")
                .status(Status.CREATED)
                .recurring(true)
                .recurrenceType(type)
                .scheduledDate(scheduledDate)
                .nextOccurrence(nextOccurrence)
                .recurrenceAnchorDate(anchor)
                .todoList(ownersList)
                .build();
    }

    private void givenList() {
        when(todoListRepository.findById(LIST_ID)).thenReturn(Optional.of(ownersList));
    }

    private void givenItem(TodoItem item) {
        when(todoItemRepository.findByIdAndTodoListId(ITEM_ID, LIST_ID)).thenReturn(Optional.of(item));
    }

    private UpdateTodoItemRequest statusUpdate(String status) {
        UpdateTodoItemRequest request = new UpdateTodoItemRequest();
        request.setStatus(status);
        return request;
    }

    private TodoItem savedItem() {
        ArgumentCaptor<TodoItem> captor = ArgumentCaptor.forClass(TodoItem.class);
        verify(todoItemRepository).save(captor.capture());
        return captor.getValue();
    }

    // ----------------------------------------------------------------- create

    @Nested
    class CreateTodoItem {

        @ParameterizedTest
        @CsvSource({
                "DAILY,   2026-03-10, 2026-03-11",
                "WEEKLY,  2026-03-10, 2026-03-17",
                "MONTHLY, 2026-03-10, 2026-04-10",
                "MONTHLY, 2026-01-31, 2026-02-28",
                "MONTHLY, 2028-01-31, 2028-02-29"
        })
        void createsRecurringTodoItem(String type, LocalDate scheduledDate, LocalDate expectedNext) {
            givenList();

            TodoItemResponse response =
                    service.createTodoItem(OWNER_ID, LIST_ID, createRequest(true, type, scheduledDate));

            TodoItem saved = savedItem();
            assertThat(saved.isRecurring()).isTrue();
            assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.valueOf(type));
            assertThat(saved.getScheduledDate()).isEqualTo(scheduledDate);
            assertThat(saved.getNextOccurrence()).isEqualTo(expectedNext);
            assertThat(saved.getRecurrenceAnchorDate()).isEqualTo(scheduledDate);
            assertThat(saved.getDeadline()).isNull();
            assertThat(saved.getStatus()).isEqualTo(Status.CREATED);
            assertThat(saved.getTodoList()).isSameAs(ownersList);

            assertThat(response.isRecurring()).isTrue();
            assertThat(response.getRecurrenceType()).isEqualTo(type);
            assertThat(response.getScheduledDate()).isEqualTo(scheduledDate);
            assertThat(response.getNextOccurrence()).isEqualTo(expectedNext);
        }

        @Test
        void createsOneTimeTodoItemAsBefore() {
            givenList();
            CreateTodoItemRequest request = createRequest(null, null, null);
            request.setTitle("Buy milk");
            request.setDeadline(LocalDate.of(2026, 1, 10));

            TodoItemResponse response = service.createTodoItem(OWNER_ID, LIST_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.isRecurring()).isFalse();
            assertThat(saved.getRecurrenceType()).isNull();
            assertThat(saved.getScheduledDate()).isNull();
            assertThat(saved.getNextOccurrence()).isNull();
            assertThat(saved.getRecurrenceAnchorDate()).isNull();

            assertThat(response.getTitle()).isEqualTo("Buy milk");
            assertThat(response.getStatus()).isEqualTo("CREATED");
            assertThat(response.getDeadline()).isEqualTo(LocalDate.of(2026, 1, 10));
            assertThat(response.isRecurring()).isFalse();
            assertThat(response.getRecurrenceType()).isNull();
            assertThat(response.getScheduledDate()).isNull();
            assertThat(response.getNextOccurrence()).isNull();
        }

        @Test
        void recurringFalseCreatesOneTimeTodoItem() {
            givenList();

            service.createTodoItem(OWNER_ID, LIST_ID, createRequest(false, null, null));

            assertThat(savedItem().isRecurring()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({"YEARLY", "daily", "''", "HOURLY"})
        void rejectsUnsupportedRecurrenceType(String type) {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(true, type, LocalDate.of(2026, 3, 10))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported recurrence type")
                    .hasMessageContaining("DAILY, WEEKLY, MONTHLY");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsMissingRecurrenceType() {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(true, null, LocalDate.of(2026, 3, 10))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recurrence type is required for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsMissingScheduledDate() {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(true, "DAILY", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Scheduled date is required for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsRecurrenceTypeOnOneTimeTodoItem() {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(null, "DAILY", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recurrence type and scheduled date are only allowed for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsScheduledDateOnOneTimeTodoItem() {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(false, null, LocalDate.of(2026, 3, 10))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recurrence type and scheduled date are only allowed for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsDeadlineOnRecurringTodoItem() {
            givenList();
            CreateTodoItemRequest request = createRequest(true, "DAILY", LocalDate.of(2026, 3, 10));
            request.setDeadline(LocalDate.of(2026, 3, 20));

            assertThatThrownBy(() -> service.createTodoItem(OWNER_ID, LIST_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Deadline is not supported for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsRecurringTodoItemCreatedAsCompleted() {
            givenList();
            CreateTodoItemRequest request = createRequest(true, "DAILY", LocalDate.of(2026, 3, 10));
            request.setStatus("COMPLETED");

            assertThatThrownBy(() -> service.createTodoItem(OWNER_ID, LIST_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("A recurring todo item cannot be created with status COMPLETED");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsCreateInAnotherUsersList() {
            givenList();

            assertThatThrownBy(() -> service.createTodoItem(
                    OTHER_USER_ID, LIST_ID, createRequest(true, "DAILY", LocalDate.of(2026, 3, 10))))
                    .isInstanceOf(ForbiddenException.class);

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsCreateInMissingList() {
            when(todoListRepository.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTodoItem(
                    OWNER_ID, LIST_ID, createRequest(true, "DAILY", LocalDate.of(2026, 3, 10))))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // --------------------------------------------------------------- complete

    @Nested
    class CompleteTodoItem {

        @ParameterizedTest
        @CsvSource({
                "DAILY,   2026-03-10, 2026-03-11, 2026-03-12",
                "WEEKLY,  2026-03-10, 2026-03-17, 2026-03-24",
                "MONTHLY, 2026-03-10, 2026-04-10, 2026-05-10",
                "MONTHLY, 2026-12-31, 2027-01-31, 2027-02-28"
        })
        void completingRecurringItemRecordsHistoryAndMovesToNextOccurrence(
                RecurrenceType type,
                LocalDate scheduled,
                LocalDate next,
                LocalDate nextAfterThat
        ) {
            givenItem(recurringItem(type, scheduled, next));

            TodoItemResponse response =
                    service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, statusUpdate("COMPLETED"));

            ArgumentCaptor<TodoItemCompletion> completion = ArgumentCaptor.forClass(TodoItemCompletion.class);
            verify(todoItemCompletionRepository).save(completion.capture());
            assertThat(completion.getValue().getTodoItem().getId()).isEqualTo(ITEM_ID);
            assertThat(completion.getValue().getOccurrenceDate()).isEqualTo(scheduled);
            assertThat(completion.getValue().getCompletedAt()).isEqualTo(NOW);

            TodoItem saved = savedItem();
            assertThat(saved.isRecurring()).isTrue();
            assertThat(saved.getStatus()).isEqualTo(Status.CREATED);
            assertThat(saved.getScheduledDate()).isEqualTo(next);
            assertThat(saved.getNextOccurrence()).isEqualTo(nextAfterThat);

            assertThat(response.getStatus()).isEqualTo("CREATED");
            assertThat(response.getScheduledDate()).isEqualTo(next);
            assertThat(response.getNextOccurrence()).isEqualTo(nextAfterThat);
        }

        @Test
        void monthlyItemAnchoredOnThe31stKeepsItsDayAcrossCompletions() {
            LocalDate jan31 = LocalDate.of(2026, 1, 31);
            TodoItem item = recurringItem(RecurrenceType.MONTHLY, jan31, LocalDate.of(2026, 2, 28));
            givenItem(item);

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, statusUpdate("COMPLETED"));
            assertThat(item.getScheduledDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(item.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 31));

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, statusUpdate("COMPLETED"));
            assertThat(item.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(item.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 4, 30));

            ArgumentCaptor<TodoItemCompletion> completions = ArgumentCaptor.forClass(TodoItemCompletion.class);
            verify(todoItemCompletionRepository, times(2)).save(completions.capture());
            assertThat(completions.getAllValues())
                    .extracting(TodoItemCompletion::getOccurrenceDate)
                    .containsExactly(jan31, LocalDate.of(2026, 2, 28));
        }

        @Test
        void completingWithOtherChangesAppliesThemFirst() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = statusUpdate("COMPLETED");
            request.setTitle("Water all plants");
            request.setRecurrenceType("WEEKLY");

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            ArgumentCaptor<TodoItemCompletion> completion = ArgumentCaptor.forClass(TodoItemCompletion.class);
            verify(todoItemCompletionRepository).save(completion.capture());
            assertThat(completion.getValue().getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 3, 10));

            TodoItem saved = savedItem();
            assertThat(saved.getTitle()).isEqualTo("Water all plants");
            assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.WEEKLY);
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 17));
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 24));
        }

        @Test
        void completingOneTimeItemBehavesAsBefore() {
            givenItem(oneTimeItem());

            TodoItemResponse response =
                    service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, statusUpdate("COMPLETED"));

            verifyNoInteractions(todoItemCompletionRepository);
            TodoItem saved = savedItem();
            assertThat(saved.getStatus()).isEqualTo(Status.COMPLETED);
            assertThat(saved.isRecurring()).isFalse();
            assertThat(saved.getDeadline()).isEqualTo(LocalDate.of(2026, 1, 10));
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        void otherStatusChangesOnRecurringItemDoNotComplete() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, statusUpdate("IN_PROGRESS"));

            verifyNoInteractions(todoItemCompletionRepository);
            TodoItem saved = savedItem();
            assertThat(saved.getStatus()).isEqualTo(Status.IN_PROGRESS);
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        void rejectsCompletingAnotherUsersItem() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));

            assertThatThrownBy(() -> service.updateTodoItem(
                    OTHER_USER_ID, LIST_ID, ITEM_ID, statusUpdate("COMPLETED")))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(todoItemCompletionRepository);
            verify(todoItemRepository, never()).save(any());
        }
    }

    // ----------------------------------------------------------------- update

    @Nested
    class UpdateTodoItem {

        @Test
        void updatingTitleKeepsSchedule() {
            LocalDate anchor = LocalDate.of(2026, 1, 31);
            givenItem(recurringItem(
                    RecurrenceType.MONTHLY, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), anchor));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setTitle("Pay rent");

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.getTitle()).isEqualTo("Pay rent");
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(saved.getRecurrenceAnchorDate()).isEqualTo(anchor);
        }

        @Test
        void resendingCurrentRecurrenceKeepsAnchor() {
            LocalDate anchor = LocalDate.of(2026, 1, 31);
            givenItem(recurringItem(
                    RecurrenceType.MONTHLY, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), anchor));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(true);
            request.setRecurrenceType("MONTHLY");
            request.setScheduledDate(LocalDate.of(2026, 2, 28));

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(saved.getRecurrenceAnchorDate()).isEqualTo(anchor);
        }

        @Test
        void changingRecurrenceTypeRecalculatesNextOccurrence() {
            givenItem(recurringItem(RecurrenceType.WEEKLY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 17)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurrenceType("MONTHLY");

            TodoItemResponse response = service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.MONTHLY);
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 4, 10));
            assertThat(response.getRecurrenceType()).isEqualTo("MONTHLY");
        }

        @Test
        void changingScheduledDateReanchorsSchedule() {
            givenItem(recurringItem(
                    RecurrenceType.MONTHLY, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                    LocalDate.of(2026, 1, 31)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setScheduledDate(LocalDate.of(2026, 3, 15));

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(saved.getRecurrenceAnchorDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        }

        @Test
        void convertsOneTimeItemToRecurring() {
            TodoItem item = oneTimeItem();
            item.setStatus(Status.COMPLETED);
            givenItem(item);
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(true);
            request.setRecurrenceType("DAILY");
            request.setScheduledDate(LocalDate.of(2026, 3, 10));

            service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            verifyNoInteractions(todoItemCompletionRepository);
            TodoItem saved = savedItem();
            assertThat(saved.isRecurring()).isTrue();
            assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.DAILY);
            assertThat(saved.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 11));
            assertThat(saved.getDeadline()).isNull();
            assertThat(saved.getStatus()).isEqualTo(Status.CREATED);
        }

        @Test
        void convertsRecurringItemToOneTime() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(false);
            request.setDeadline(LocalDate.of(2026, 4, 1));

            TodoItemResponse response = service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            TodoItem saved = savedItem();
            assertThat(saved.isRecurring()).isFalse();
            assertThat(saved.getRecurrenceType()).isNull();
            assertThat(saved.getScheduledDate()).isNull();
            assertThat(saved.getNextOccurrence()).isNull();
            assertThat(saved.getRecurrenceAnchorDate()).isNull();
            assertThat(saved.getDeadline()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(response.isRecurring()).isFalse();
        }

        @Test
        void updatesOneTimeItemAsBefore() {
            givenItem(oneTimeItem());
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setTitle("Buy oat milk");
            request.setDeadline(LocalDate.of(2026, 1, 12));

            TodoItemResponse response = service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request);

            assertThat(response.getTitle()).isEqualTo("Buy oat milk");
            assertThat(response.getStatus()).isEqualTo("CREATED");
            assertThat(response.getDeadline()).isEqualTo(LocalDate.of(2026, 1, 12));
            assertThat(response.isRecurring()).isFalse();
        }

        @Test
        void rejectsConvertingToRecurringWithoutScheduledDate() {
            givenItem(oneTimeItem());
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(true);
            request.setRecurrenceType("DAILY");

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Scheduled date is required for recurring todo items");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsConvertingToRecurringWithoutRecurrenceType() {
            givenItem(oneTimeItem());
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(true);
            request.setScheduledDate(LocalDate.of(2026, 3, 10));

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recurrence type is required for recurring todo items");
        }

        @Test
        void rejectsUnsupportedRecurrenceType() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurrenceType("FORTNIGHTLY");

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported recurrence type 'FORTNIGHTLY'");

            verify(todoItemRepository, never()).save(any());
        }

        @Test
        void rejectsDeadlineOnRecurringItem() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setDeadline(LocalDate.of(2026, 3, 20));

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Deadline is not supported for recurring todo items");
        }

        @Test
        void rejectsRecurrenceDetailsOnOneTimeItem() {
            givenItem(oneTimeItem());
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setScheduledDate(LocalDate.of(2026, 3, 10));

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recurrence type and scheduled date are only allowed for recurring todo items");
        }

        @Test
        void rejectsRecurrenceDetailsWhenConvertingToOneTime() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurring(false);
            request.setRecurrenceType("WEEKLY");

            assertThatThrownBy(() -> service.updateTodoItem(OWNER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void rejectsUpdatingAnotherUsersItem() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));
            UpdateTodoItemRequest request = new UpdateTodoItemRequest();
            request.setRecurrenceType("WEEKLY");

            assertThatThrownBy(() -> service.updateTodoItem(OTHER_USER_ID, LIST_ID, ITEM_ID, request))
                    .isInstanceOf(ForbiddenException.class);

            verify(todoItemRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------- get and delete

    @Nested
    class GetAndDeleteTodoItem {

        @Test
        void getsRecurringItemWithScheduleDetails() {
            givenItem(recurringItem(RecurrenceType.WEEKLY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 17)));

            TodoItemResponse response = service.getTodoItem(OWNER_ID, LIST_ID, ITEM_ID);

            assertThat(response.isRecurring()).isTrue();
            assertThat(response.getRecurrenceType()).isEqualTo("WEEKLY");
            assertThat(response.getScheduledDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(response.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 3, 17));
        }

        @Test
        void rejectsGettingAnotherUsersItem() {
            givenItem(recurringItem(RecurrenceType.WEEKLY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 17)));

            assertThatThrownBy(() -> service.getTodoItem(OTHER_USER_ID, LIST_ID, ITEM_ID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void deletesRecurringItem() {
            TodoItem item = recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11));
            givenItem(item);

            TodoItemResponse response = service.deleteTodoItem(OWNER_ID, LIST_ID, ITEM_ID);

            verify(todoItemRepository).delete(item);
            assertThat(response.getId()).isEqualTo(ITEM_ID);
            assertThat(response.isRecurring()).isTrue();
            assertThat(response.getRecurrenceType()).isEqualTo("DAILY");
        }

        @Test
        void rejectsDeletingAnotherUsersItem() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));

            assertThatThrownBy(() -> service.deleteTodoItem(OTHER_USER_ID, LIST_ID, ITEM_ID))
                    .isInstanceOf(ForbiddenException.class);

            verify(todoItemRepository, never()).delete(any());
        }
    }

    // ------------------------------------------------------ completion history

    @Nested
    class CompletionHistory {

        @Test
        void returnsCompletionHistoryOfOwnItem() {
            TodoItem item = recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 13));
            givenItem(item);
            when(todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(ITEM_ID))
                    .thenReturn(List.of(
                            new TodoItemCompletion(1L, item, LocalDate.of(2026, 3, 10), NOW),
                            new TodoItemCompletion(2L, item, LocalDate.of(2026, 3, 11), NOW.plusSeconds(60))
                    ));

            List<TodoItemCompletionResponse> history =
                    service.getTodoItemCompletions(OWNER_ID, LIST_ID, ITEM_ID);

            assertThat(history)
                    .extracting(TodoItemCompletionResponse::getOccurrenceDate)
                    .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11));
            assertThat(history.get(0).getId()).isEqualTo(1L);
            assertThat(history.get(0).getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        void rejectsReadingAnotherUsersHistory() {
            givenItem(recurringItem(RecurrenceType.DAILY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)));

            assertThatThrownBy(() -> service.getTodoItemCompletions(OTHER_USER_ID, LIST_ID, ITEM_ID))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(todoItemCompletionRepository);
        }

        @Test
        void rejectsHistoryOfItemOutsideList() {
            when(todoItemRepository.findByIdAndTodoListId(ITEM_ID, LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTodoItemCompletions(OWNER_ID, LIST_ID, ITEM_ID))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(todoItemCompletionRepository);
        }
    }
}
