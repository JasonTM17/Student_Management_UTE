package io.campuscore.restfulapi.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins DemoAccountGate's email list to the V82 migration's activation list.
 *
 * <p>V82 activates exactly the runbook-documented .demo accounts, and the gate
 * re-applies ACTIVE/LOCKED to the same population on every boot depending on
 * {@code DEMO_ACCOUNTS_ENABLED}. If a future seeded account is added to one
 * side and forgotten on the other, the migration and the runtime gate would
 * disagree about who may log in — on a public host that disagreement is an
 * open admin login, so this parity is a security invariant, not cosmetics.
 */
class DemoAccountGateRunbookParityTest {

    private static final Path V82 =
            Path.of("src/main/resources/db/migration/V82__activate_runbook_demo_accounts.sql");
    private static final Path V77 =
            Path.of("src/main/resources/db/migration/V77__align_demo_account_passwords.sql");
    private static final Pattern EMAIL = Pattern.compile("'([A-Za-z0-9._-]+@campuscore\\.(?:demo|edu))'");

    @Test
    void gateCoversEveryAccountV82Activates() throws Exception {
        Set<String> activated = quotedEmails(V82);
        assertThat(activated).isNotEmpty();
        assertThat(activated).allMatch(DemoAccountGate.DEMO_EMAILS::contains);
    }

    @Test
    void v82ActivatesExactlyTheRunbookSubsetV77Aligned() throws Exception {
        Set<String> aligned = quotedEmails(V77);
        // V77 aligned the .edu admin's password too, but that account is
        // activated by V50, so V82 must re-activate precisely the .demo set.
        aligned.remove("admin@campuscore.edu");
        assertThat(quotedEmails(V82)).isEqualTo(aligned);
    }

    @Test
    void gateStillCoversTheShowcaseTrio() {
        assertThat(DemoAccountGate.DEMO_EMAILS).containsAll(List.of(
                "student@campuscore.edu", "lecturer@campuscore.edu", "admin@campuscore.edu"));
    }

    private static Set<String> quotedEmails(Path migration) throws Exception {
        assertThat(migration).exists();
        Matcher matcher = EMAIL.matcher(Files.readString(migration));
        Set<String> emails = new HashSet<>();
        while (matcher.find()) {
            emails.add(matcher.group(1));
        }
        return emails;
    }
}
