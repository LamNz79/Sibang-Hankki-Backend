package com.sibang.hankki.reservation.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ReservationProposalMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesTableChecksAndOnePendingPartialIndex() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = current_schema() and table_name = 'reservation_proposals'
                """, Integer.class);
        assertEquals(1, tableCount);

        String checks = jdbcTemplate.queryForObject("""
                select string_agg(pg_get_constraintdef(oid), ' ')
                from pg_constraint
                where conrelid = 'reservation_proposals'::regclass and contype = 'c'
                """, String.class);
        assertTrue(checks.contains("proposed_party_size > 0"));
        assertTrue(checks.contains("PENDING") && checks.contains("ACCEPTED")
                && checks.contains("DECLINED") && checks.contains("EXPIRED"));
        assertTrue(checks.contains("RESTAURANT") && checks.contains("CUSTOMER"));

        String createdByNullable = jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'reservation_proposals'
                  and column_name = 'created_by'
                """, String.class);
        assertEquals("YES", createdByNullable);

        String indexDefinition = jdbcTemplate.queryForObject("""
                select indexdef from pg_indexes
                where schemaname = current_schema()
                  and tablename = 'reservation_proposals'
                  and indexname = 'reservation_proposals_one_pending_idx'
                """, String.class);
        assertTrue(indexDefinition.contains("UNIQUE"));
        assertTrue(indexDefinition.contains("WHERE") && indexDefinition.contains("status"));
    }
}
