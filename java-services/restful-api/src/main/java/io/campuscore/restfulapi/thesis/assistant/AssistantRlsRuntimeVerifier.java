package io.campuscore.restfulapi.thesis.assistant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verifies that the dedicated login actually encounters enabled RLS policies.
 *
 * <p>On success the shared {@link AssistantRlsState} flips to VERIFIED. On any
 * failure the boot continues (Wave 1.2): the verifier logs an ERROR and leaves
 * the state DEGRADED, so assistant chat endpoints answer 503
 * ASSISTANT_RLS_UNAVAILABLE while the rest of the API keeps serving. Environments
 * provisioned by {@code scripts/provision-assistant-runtime.mjs} (Render
 * production included) still verify and are unaffected.</p>
 */
@Component
@Profile("persistence & !test")
public class AssistantRlsRuntimeVerifier implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(AssistantRlsRuntimeVerifier.class);
    private static final String VERIFY_SQL = """
            SELECT current_user, session_user, r.rolcanlogin, r.rolsuper, r.rolcreatedb,
                   r.rolcreaterole, r.rolinherit, r.rolreplication, r.rolbypassrls, r.rolconnlimit,
                   (SELECT count(*) FROM pg_class c
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND c.relkind IN ('r','p') AND c.relowner=r.oid) AS owned_tables,
                   (SELECT count(*) FROM pg_class c
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND c.relkind IN ('r','p') AND c.relrowsecurity) AS rls_tables,
                   (SELECT count(*) FROM pg_class c
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND c.relkind IN ('r','p') AND c.relforcerowsecurity) AS forced_rls_tables,
                   (SELECT count(*) FROM pg_class c
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND c.relkind IN ('r','p')) AS assistant_tables,
                   (SELECT count(DISTINCT tablename) FROM pg_policies WHERE schemaname='assistant') AS policy_tables,
                   (SELECT count(*) FROM pg_policies WHERE schemaname='assistant') AS policy_count,
                   (SELECT count(*) FROM pg_policies
                     WHERE schemaname='assistant'
                       AND roles <> ARRAY['campuscore_assistant_runtime']::name[]) AS policies_for_other_roles,
                   (SELECT count(*)
                      FROM pg_auth_members m
                      LEFT JOIN pg_roles member_role ON member_role.oid = m.member
                     WHERE (m.member=r.oid OR m.roleid=r.oid)
                       AND NOT (m.roleid=r.oid AND member_role.rolname='postgres')) AS role_memberships,
                   (SELECT count(*)
                      FROM unnest(COALESCE(r.rolconfig, ARRAY[]::text[])) cfg(value)
                     WHERE split_part(cfg.value, '=', 1) LIKE 'app.assistant.%') AS role_context_defaults,
                   COALESCE(current_setting('app.assistant.owner_id', true), '') AS session_owner_id,
                   COALESCE(current_setting('app.assistant.scope', true), '') AS session_scope,
                   COALESCE(current_setting('app.assistant.admin', true), '') AS session_admin,
                   (SELECT count(*) FROM pg_class c
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND c.relkind IN ('v','m','f')) AS unreviewed_relations,
                   (SELECT count(*) FROM pg_proc p
                     JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname='assistant') AS assistant_routines,
                   (SELECT count(*) FROM pg_trigger t
                     JOIN pg_class c ON c.oid=t.tgrelid
                     JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='assistant' AND NOT t.tgisinternal) AS assistant_triggers,
                   (SELECT count(*)
                      FROM pg_roles a
                     WHERE a.rolname = ANY (ARRAY['anon','authenticated','service_role','authenticator'])
                       AND (
                           has_schema_privilege(a.oid, 'assistant', 'USAGE')
                           OR has_schema_privilege(a.oid, 'assistant', 'CREATE')
                           OR EXISTS (
                               SELECT 1 FROM pg_class c
                               JOIN pg_namespace n ON n.oid=c.relnamespace
                               WHERE n.nspname='assistant'
                                 AND c.relkind IN ('r','p','v','m','f')
                                 AND (has_table_privilege(a.oid,c.oid,'SELECT')
                                   OR has_table_privilege(a.oid,c.oid,'INSERT')
                                   OR has_table_privilege(a.oid,c.oid,'UPDATE')
                                   OR has_table_privilege(a.oid,c.oid,'DELETE')
                                   OR has_table_privilege(a.oid,c.oid,'TRUNCATE')
                                   OR has_table_privilege(a.oid,c.oid,'REFERENCES')
                                   OR has_table_privilege(a.oid,c.oid,'TRIGGER'))
                           )
                           OR EXISTS (
                               SELECT 1 FROM pg_class c
                               JOIN pg_namespace n ON n.oid=c.relnamespace
                               WHERE n.nspname='assistant' AND c.relkind='S'
                                 AND (has_sequence_privilege(a.oid,c.oid,'USAGE')
                                   OR has_sequence_privilege(a.oid,c.oid,'SELECT')
                                   OR has_sequence_privilege(a.oid,c.oid,'UPDATE'))
                           )
                           OR EXISTS (
                               SELECT 1 FROM pg_proc p
                               JOIN pg_namespace n ON n.oid=p.pronamespace
                               WHERE n.nspname='assistant' AND has_function_privilege(a.oid,p.oid,'EXECUTE')
                           )
                       )) AS api_roles_with_access,
                   current_setting('server_version_num')::integer AS server_version_num
              FROM pg_roles r
             WHERE r.rolname=current_user
            """;

    private final DataSource dataSource;
    private final AssistantRlsState state;

    public AssistantRlsRuntimeVerifier(
            @org.springframework.beans.factory.annotation.Qualifier(AssistantDatabaseConfiguration.DATA_SOURCE)
            DataSource assistantDataSource,
            AssistantRlsState state) {
        this.dataSource = assistantDataSource;
        this.state = state;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(VERIFY_SQL);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()
                    || !AssistantDatabaseConfiguration.RUNTIME_ROLE.equals(result.getString("current_user"))
                    || !AssistantDatabaseConfiguration.RUNTIME_ROLE.equals(result.getString("session_user"))
                    || !result.getBoolean("rolcanlogin")
                    || result.getBoolean("rolsuper")
                    || result.getBoolean("rolcreatedb")
                    || result.getBoolean("rolcreaterole")
                    || result.getBoolean("rolinherit")
                    || result.getBoolean("rolreplication")
                    || result.getBoolean("rolbypassrls")
                    || result.getInt("rolconnlimit") != 8
                    || result.getLong("owned_tables") != 0
                    || result.getLong("rls_tables") != 13
                    || result.getLong("forced_rls_tables") != 13
                    || result.getLong("assistant_tables") != 13
                    || result.getLong("policy_tables") != 13
                    || result.getLong("policy_count") != 45
                    || result.getLong("policies_for_other_roles") != 0
                    || result.getLong("role_memberships") != 0
                    || result.getLong("role_context_defaults") != 0
                    || !result.getString("session_owner_id").isEmpty()
                    || !result.getString("session_scope").isEmpty()
                    || !result.getString("session_admin").isEmpty()
                    || result.getLong("unreviewed_relations") != 0
                    || result.getLong("assistant_routines") != 0
                    || result.getLong("assistant_triggers") != 0
                    || result.getLong("api_roles_with_access") != 0
                    || result.getInt("server_version_num") < 150000) {
                String reason = "Assistant RLS runtime database role or policy verification failed";
                LOG.error("{}; assistant chat endpoints will answer 503 {} until provisioning is repaired",
                        reason, UnavailableAssistantDataSource.UNAVAILABLE_REASON);
                state.markDegraded(reason);
                return;
            }
            if (result.next()) {
                String reason = "Assistant runtime identity query returned multiple rows";
                LOG.error("{}; assistant chat endpoints will answer 503 {}",
                        reason, UnavailableAssistantDataSource.UNAVAILABLE_REASON);
                state.markDegraded(reason);
                return;
            }
            state.markVerified("Assistant RLS runtime role, policies and isolation boundary verified");
            LOG.info("Assistant RLS runtime verification passed");
        } catch (Exception failure) {
            // Deliberately degrade instead of killing the boot (Wave 1.2): a
            // missing role or password must not take down registration,
            // catalog, and thesis flows that do not depend on the assistant.
            String reason = failure instanceof IllegalStateException stateFailure
                    ? stateFailure.getMessage()
                    : "Assistant RLS runtime database verification failed: "
                            + failure.getClass().getSimpleName();
            LOG.error("{}; assistant chat endpoints will answer 503 {} until provisioning is repaired",
                    reason, UnavailableAssistantDataSource.UNAVAILABLE_REASON, failure);
            state.markDegraded(reason);
        }
    }
}
