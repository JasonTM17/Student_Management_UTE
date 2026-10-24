package io.campuscore.restfulapi.thesis.assistant.rls;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import io.campuscore.restfulapi.thesis.assistant.config.AssistantDatabaseConfiguration;

class AssistantRlsRuntimeVerifierTest {

    private static Map<String, Object> passingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("current_user", AssistantDatabaseConfiguration.RUNTIME_ROLE);
        row.put("session_user", AssistantDatabaseConfiguration.RUNTIME_ROLE);
        row.put("rolcanlogin", true);
        row.put("rolsuper", false);
        row.put("rolcreatedb", false);
        row.put("rolcreaterole", false);
        row.put("rolinherit", false);
        row.put("rolreplication", false);
        row.put("rolbypassrls", false);
        row.put("rolconnlimit", 8);
        row.put("owned_tables", 0L);
        row.put("rls_tables", 13L);
        row.put("forced_rls_tables", 13L);
        row.put("assistant_tables", 13L);
        row.put("policy_tables", 13L);
        row.put("policy_count", 59L);
        row.put("policies_for_other_roles", 0L);
        row.put("role_memberships", 0L);
        row.put("role_context_defaults", 0L);
        row.put("session_owner_id", "");
        row.put("session_scope", "");
        row.put("session_admin", "");
        row.put("unreviewed_relations", 0L);
        row.put("assistant_routines", 0L);
        row.put("assistant_triggers", 0L);
        row.put("can_update_conversation_scope", true);
        row.put("api_roles_with_access", 0L);
        row.put("server_version_num", 180006);
        return row;
    }

    private static AssistantRlsState runVerifier(Map<String, Object> row) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));
        when(result.getLong(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));
        when(result.getInt(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));
        when(result.getBoolean(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));

        AssistantRlsState state = new AssistantRlsState();
        new AssistantRlsRuntimeVerifier(dataSource, state).afterSingletonsInstantiated();
        return state;
    }

    @Test
    void acceptsLeastPrivilegedRuntimeWithAllControls() throws Exception {
        AssistantRlsState state = runVerifier(passingRow());
        assertTrue(state.verified());
        assertEquals(AssistantRlsState.Verdict.VERIFIED, state.verdict());
    }

    @Test
    void rejectsAnUnexpectedRoleMembership() throws Exception {
        Map<String, Object> row = passingRow();
        row.put("role_memberships", 1L);
        AssistantRlsState state = runVerifier(row);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, state.verdict());
        assertFalse(state.verified());
    }

    @Test
    void rejectsWrongPolicyOrTableCounts() throws Exception {
        Map<String, Object> wrongPolicyCount = passingRow();
        wrongPolicyCount.put("policy_count", 58L);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(wrongPolicyCount).verdict());

        Map<String, Object> wrongTableCount = passingRow();
        wrongTableCount.put("rls_tables", 12L);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(wrongTableCount).verdict());

        Map<String, Object> foreignPolicy = passingRow();
        foreignPolicy.put("policies_for_other_roles", 1L);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(foreignPolicy).verdict());
    }

    @Test
    void rejectsElevatedRoleAttributesOrSharedAccess() throws Exception {
        Map<String, Object> elevated = passingRow();
        elevated.put("rolbypassrls", true);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(elevated).verdict());

        Map<String, Object> apiAccess = passingRow();
        apiAccess.put("api_roles_with_access", 1L);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(apiAccess).verdict());

        Map<String, Object> noScopeUpdate = passingRow();
        noScopeUpdate.put("can_update_conversation_scope", false);
        assertEquals(AssistantRlsState.Verdict.DEGRADED, runVerifier(noScopeUpdate).verdict());
    }

    @Test
    void degradesOnEmptyResultOrFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet empty = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(empty);
        when(empty.next()).thenReturn(false);
        AssistantRlsState emptyState = new AssistantRlsState();
        new AssistantRlsRuntimeVerifier(dataSource, emptyState).afterSingletonsInstantiated();
        assertEquals(AssistantRlsState.Verdict.DEGRADED, emptyState.verdict());

        DataSource failing = mock(DataSource.class);
        when(failing.getConnection()).thenThrow(new SQLException("refused"));
        AssistantRlsState failedState = new AssistantRlsState();
        new AssistantRlsRuntimeVerifier(failing, failedState).afterSingletonsInstantiated();
        assertEquals(AssistantRlsState.Verdict.DEGRADED, failedState.verdict());
    }

    @Test
    void verifyQueryContainsTheNarrowNeonOwnerEdgeException() throws Exception {
        Field sql = AssistantRlsRuntimeVerifier.class.getDeclaredField("VERIFY_SQL");
        sql.setAccessible(true);
        String verifySql = (String) sql.get(null);
        assertTrue(verifySql.contains("pg_auth_members"));
        assertTrue(verifySql.contains("neondb_owner"), "the Neon owner-membership edge must be recognized");
        assertTrue(verifySql.contains("cloud_admin"), "the exception must pin the cloud_admin grantor");
        assertTrue(verifySql.contains("admin_option"));
        assertTrue(verifySql.contains("inherit_option"));
        assertTrue(verifySql.contains("set_option"));
    }
}
