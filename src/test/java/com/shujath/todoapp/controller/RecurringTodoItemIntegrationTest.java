package com.shujath.todoapp.controller;

import com.jayway.jsonpath.JsonPath;
import com.shujath.todoapp.repository.TodoItemCompletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringTodoItemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TodoItemCompletionRepository todoItemCompletionRepository;

    private String ownerToken;
    private String otherUserToken;
    private long listId;

    @BeforeEach
    void setUp() throws Exception {
        ownerToken = registerAndLogin();
        otherUserToken = registerAndLogin();
        listId = createList(ownerToken, "Chores");
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "username": "user", "password": "password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }

    private long createList(String token, String name) throws Exception {
        String body = mockMvc.perform(authorized(post("/api/v1/lists"), token)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String itemsUrl() {
        return "/api/v1/lists/" + listId + "/items";
    }

    private String itemUrl(long itemId) {
        return itemsUrl() + "/" + itemId;
    }

    private ResultActions createItem(String json) throws Exception {
        return mockMvc.perform(authorized(post(itemsUrl()), ownerToken).content(json));
    }

    private long createRecurringItem(String type, String scheduledDate) throws Exception {
        String body = createItem("""
                {"title": "Water plants", "status": "CREATED", "recurring": true,
                 "recurrenceType": "%s", "scheduledDate": "%s"}
                """.formatted(type, scheduledDate))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions complete(long itemId, String token) throws Exception {
        return mockMvc.perform(authorized(put(itemUrl(itemId)), token)
                .content("{\"status\": \"COMPLETED\"}"));
    }

    // ----------------------------------------------------------------- create

    @Test
    void createsDailyWeeklyAndMonthlyRecurringItems() throws Exception {
        createItem("""
                {"title": "Stretch", "status": "CREATED", "recurring": true,
                 "recurrenceType": "DAILY", "scheduledDate": "2026-03-10"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Stretch"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.recurrenceType").value("DAILY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-03-10"))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-03-11"));

        createItem("""
                {"title": "Take out bins", "status": "CREATED", "recurring": true,
                 "recurrenceType": "WEEKLY", "scheduledDate": "2026-03-10"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceType").value("WEEKLY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-03-10"))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-03-17"));

        createItem("""
                {"title": "Pay rent", "status": "CREATED", "recurring": true,
                 "recurrenceType": "MONTHLY", "scheduledDate": "2027-01-31"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceType").value("MONTHLY"))
                .andExpect(jsonPath("$.scheduledDate").value("2027-01-31"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-02-28"));

        mockMvc.perform(authorized(get(itemsUrl()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].recurrenceType", containsInAnyOrder("DAILY", "WEEKLY", "MONTHLY")))
                .andExpect(jsonPath("$[*].recurring", everyItem(is(true))));
    }

    @Test
    void monthlyItemScheduledOnLeapYearJanuary31stMovesToFebruary29th() throws Exception {
        createItem("""
                {"title": "Pay rent", "status": "CREATED", "recurring": true,
                 "recurrenceType": "MONTHLY", "scheduledDate": "2028-01-31"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextOccurrence").value("2028-02-29"));
    }

    static Stream<Arguments> invalidRecurrenceInput() {
        return Stream.of(
                Arguments.of("unsupported recurrence type", """
                        {"title": "t", "status": "CREATED", "recurring": true,
                         "recurrenceType": "YEARLY", "scheduledDate": "2026-03-10"}
                        """, "Unsupported recurrence type 'YEARLY', allowed values are [DAILY, WEEKLY, MONTHLY]"),
                Arguments.of("missing recurrence type", """
                        {"title": "t", "status": "CREATED", "recurring": true, "scheduledDate": "2026-03-10"}
                        """, "Recurrence type is required for recurring todo items"),
                Arguments.of("missing scheduled date", """
                        {"title": "t", "status": "CREATED", "recurring": true, "recurrenceType": "DAILY"}
                        """, "Scheduled date is required for recurring todo items"),
                Arguments.of("recurrence type without recurring flag", """
                        {"title": "t", "status": "CREATED", "recurrenceType": "DAILY", "scheduledDate": "2026-03-10"}
                        """, "Recurrence type and scheduled date are only allowed for recurring todo items"),
                Arguments.of("deadline on recurring item", """
                        {"title": "t", "status": "CREATED", "recurring": true, "recurrenceType": "DAILY",
                         "scheduledDate": "2026-03-10", "deadline": "2026-03-20"}
                        """, "Deadline is not supported for recurring todo items, use scheduledDate instead"),
                Arguments.of("recurring item created as completed", """
                        {"title": "t", "status": "COMPLETED", "recurring": true,
                         "recurrenceType": "DAILY", "scheduledDate": "2026-03-10"}
                        """, "A recurring todo item cannot be created with status COMPLETED"),
                Arguments.of("non-existent date", """
                        {"title": "t", "status": "CREATED", "recurring": true,
                         "recurrenceType": "MONTHLY", "scheduledDate": "2026-02-30"}
                        """, "Malformed request body, dates must use the yyyy-MM-dd format"),
                Arguments.of("unparseable date", """
                        {"title": "t", "status": "CREATED", "recurring": true,
                         "recurrenceType": "MONTHLY", "scheduledDate": "next tuesday"}
                        """, "Malformed request body, dates must use the yyyy-MM-dd format")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRecurrenceInput")
    void rejectsInvalidRecurrenceInput(String description, String json, String expectedMessage) throws Exception {
        createItem(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(authorized(get(itemsUrl()), ownerToken))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --------------------------------------------------------------- complete

    @Test
    void completingRecurringItemRecordsHistoryAndMovesToNextOccurrence() throws Exception {
        long itemId = createRecurringItem("MONTHLY", "2027-01-31");

        complete(itemId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.scheduledDate").value("2027-02-28"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-03-31"));

        complete(itemId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDate").value("2027-03-31"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-04-30"));

        mockMvc.perform(authorized(get(itemUrl(itemId)), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.scheduledDate").value("2027-03-31"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-04-30"));

        mockMvc.perform(authorized(get(itemUrl(itemId) + "/completions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].occurrenceDate").value("2027-01-31"))
                .andExpect(jsonPath("$[0].completedAt").isString())
                .andExpect(jsonPath("$[1].occurrenceDate").value("2027-02-28"));

        assertThat(todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(itemId))
                .hasSize(2);
    }

    @Test
    void completingDailyAndWeeklyItemsMovesThemForward() throws Exception {
        long daily = createRecurringItem("DAILY", "2026-12-31");
        long weekly = createRecurringItem("WEEKLY", "2026-12-29");

        complete(daily, ownerToken)
                .andExpect(jsonPath("$.scheduledDate").value("2027-01-01"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-01-02"));

        complete(weekly, ownerToken)
                .andExpect(jsonPath("$.scheduledDate").value("2027-01-05"))
                .andExpect(jsonPath("$.nextOccurrence").value("2027-01-12"));
    }

    // ----------------------------------------------------------------- update

    @Test
    void updatesRecurringItem() throws Exception {
        long itemId = createRecurringItem("WEEKLY", "2026-03-10");

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"title\": \"Water all plants\", \"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Water all plants"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.recurrenceType").value("WEEKLY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-03-10"))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-03-17"));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"recurrenceType\": \"MONTHLY\", \"scheduledDate\": \"2026-05-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceType").value("MONTHLY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-05-31"))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-06-30"));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"recurrenceType\": \"HOURLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Unsupported recurrence type 'HOURLY'")));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"deadline\": \"2026-06-01\"}"))
                .andExpect(status().isBadRequest());

        // Rejected updates leave the item untouched
        mockMvc.perform(authorized(get(itemUrl(itemId)), ownerToken))
                .andExpect(jsonPath("$.recurrenceType").value("MONTHLY"))
                .andExpect(jsonPath("$.deadline").value(nullValue()));
    }

    @Test
    void convertsBetweenOneTimeAndRecurring() throws Exception {
        String body = createItem("""
                {"title": "Buy milk", "status": "CREATED", "deadline": "2026-01-10"}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"recurring\": true, \"recurrenceType\": \"DAILY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Scheduled date is required for recurring todo items"));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("""
                                {"recurring": true, "recurrenceType": "DAILY", "scheduledDate": "2026-01-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.scheduledDate").value("2026-01-10"))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-01-11"));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"recurring\": false, \"deadline\": \"2026-02-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurring").value(false))
                .andExpect(jsonPath("$.recurrenceType").value(nullValue()))
                .andExpect(jsonPath("$.scheduledDate").value(nullValue()))
                .andExpect(jsonPath("$.nextOccurrence").value(nullValue()))
                .andExpect(jsonPath("$.deadline").value("2026-02-01"));
    }

    // ----------------------------------------------------------------- delete

    @Test
    void deletingRecurringItemRemovesItAndItsHistory() throws Exception {
        long itemId = createRecurringItem("DAILY", "2026-03-10");
        complete(itemId, ownerToken).andExpect(status().isOk());
        assertThat(todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(itemId)).hasSize(1);

        mockMvc.perform(authorized(delete(itemUrl(itemId)), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.recurrenceType").value("DAILY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-03-11"));

        mockMvc.perform(authorized(get(itemUrl(itemId)), ownerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(get(itemUrl(itemId) + "/completions"), ownerToken))
                .andExpect(status().isNotFound());

        assertThat(todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(itemId)).isEmpty();
    }

    @Test
    void deletingListRemovesRecurringItemsAndHistory() throws Exception {
        long itemId = createRecurringItem("WEEKLY", "2026-03-10");
        complete(itemId, ownerToken).andExpect(status().isOk());

        mockMvc.perform(authorized(delete("/api/v1/lists/" + listId), ownerToken))
                .andExpect(status().isOk());

        assertThat(todoItemCompletionRepository.findByTodoItemIdOrderByCompletedAtAscIdAsc(itemId)).isEmpty();
    }

    // -------------------------------------------------------------- ownership

    @Test
    void otherUserCannotAccessOrModifyRecurringItemOrItsHistory() throws Exception {
        long itemId = createRecurringItem("DAILY", "2026-03-10");
        complete(itemId, ownerToken).andExpect(status().isOk());

        mockMvc.perform(authorized(post(itemsUrl()), otherUserToken)
                        .content("""
                                {"title": "t", "status": "CREATED", "recurring": true,
                                 "recurrenceType": "DAILY", "scheduledDate": "2026-03-10"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(get(itemsUrl()), otherUserToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(get(itemUrl(itemId)), otherUserToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(put(itemUrl(itemId)), otherUserToken)
                        .content("{\"recurrenceType\": \"WEEKLY\"}"))
                .andExpect(status().isForbidden());

        complete(itemId, otherUserToken)
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(get(itemUrl(itemId) + "/completions"), otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not allowed to view the completion history of this todo item"));

        mockMvc.perform(authorized(delete(itemUrl(itemId)), otherUserToken))
                .andExpect(status().isForbidden());

        // Addressing the item through the other user's own list does not reach it either
        long otherUsersList = createList(otherUserToken, "Mine");
        String viaOwnList = "/api/v1/lists/" + otherUsersList + "/items/" + itemId;
        mockMvc.perform(authorized(get(viaOwnList + "/completions"), otherUserToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(put(viaOwnList), otherUserToken)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isNotFound());

        // Nothing changed for the owner
        mockMvc.perform(authorized(get(itemUrl(itemId)), ownerToken))
                .andExpect(jsonPath("$.recurrenceType").value("DAILY"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-03-11"));
        mockMvc.perform(authorized(get(itemUrl(itemId) + "/completions"), ownerToken))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void completionHistoryRequiresAuthentication() throws Exception {
        long itemId = createRecurringItem("DAILY", "2026-03-10");

        mockMvc.perform(get(itemUrl(itemId) + "/completions"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get(itemUrl(itemId) + "/completions")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------ one-time behaviour

    @Test
    void oneTimeItemsBehaveAsBefore() throws Exception {
        String body = createItem("""
                {"title": "Buy milk", "status": "CREATED", "deadline": "2026-01-10"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.deadline").value("2026-01-10"))
                .andExpect(jsonPath("$.recurring").value(false))
                .andExpect(jsonPath("$.recurrenceType").value(nullValue()))
                .andExpect(jsonPath("$.scheduledDate").value(nullValue()))
                .andExpect(jsonPath("$.nextOccurrence").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(body, "$.id")).longValue();

        complete(itemId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deadline").value("2026-01-10"))
                .andExpect(jsonPath("$.recurring").value(false));

        mockMvc.perform(authorized(get(itemUrl(itemId)), ownerToken))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(authorized(get(itemUrl(itemId) + "/completions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(authorized(put(itemUrl(itemId)), ownerToken)
                        .content("{\"scheduledDate\": \"2026-03-10\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authorized(delete(itemUrl(itemId)), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
