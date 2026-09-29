alter table todo_items
    add column recurring boolean not null default false;

alter table todo_items
    add column recurrence_type enum ('DAILY', 'WEEKLY', 'MONTHLY') null;

alter table todo_items
    add column scheduled_date date null;

alter table todo_items
    add column next_occurrence date null;

-- Date the recurrence schedule is calculated from. Monthly recurrences keep its
-- day-of-month, so Jan 31 -> Feb 28 -> Mar 31 instead of drifting to the 28th.
alter table todo_items
    add column recurrence_anchor_date date null;

alter table todo_items
    add constraint todo_items_recurrence_chk
        check (
            (recurring = false
                and recurrence_type is null
                and scheduled_date is null
                and next_occurrence is null
                and recurrence_anchor_date is null)
            or
            (recurring = true
                and recurrence_type is not null
                and scheduled_date is not null
                and next_occurrence is not null
                and recurrence_anchor_date is not null)
        );

create table todo_item_completions
(
    id              bigint auto_increment
        primary key,
    todo_item_id    bigint      not null,
    occurrence_date date        not null,
    completed_at    datetime(6) not null,
    constraint todo_item_completions_todo_items_id_fk
        foreign key (todo_item_id) references todo_items (id)
            on delete cascade
);
