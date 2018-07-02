--liquibase formatted sql

--changeset letsautomate:1
CREATE TABLE IF NOT EXISTS account (
   accountId varchar(50) PRIMARY KEY,
   payload json NOT NULL
);
--rollback drop table account;


--changeset letsautomate:2
CREATE TABLE IF NOT EXISTS certificate_events (
   unique_id varchar(100) PRIMARY KEY,
   entity_id varchar(100) NOT NULL,
   sequence_num bigint NOT NULL,
   event_type varchar(100) NOT NULL,
   version varchar(50) NOT NULL,
   event json NOT NULL,
   created_at timestamp NOT NULL,
   metadata json NOT NULL,
   UNIQUE (entity_id, sequence_num)
);
--rollback drop table certificate_events;


--changeset letsautomate:3
CREATE TABLE IF NOT EXISTS certificate_events_offsets (
  group_id varchar(100) PRIMARY KEY,
  sequence_num bigint NOT NULL
)
--rollback drop table certificate_events_offsets;