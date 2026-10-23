package com.nekocong.text2sql.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nekocong.text2sql.conversation.Conversation;
import com.nekocong.text2sql.repository.UserRepository;
import com.nekocong.text2sql.security.Roles;
import com.nekocong.text2sql.service.ConversationService;
import com.nekocong.text2sql.user.AppUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Loads the {@code db/app-data.sql} seed script into the test database and asserts the dataset it
 * produces, including the role assignments that the data-scope rules depend on.
 *
 * <p>This keeps the seed script honest: a syntax error, a wrong role or a missing conversation is
 * caught here instead of on a fresh server.
 */
@SpringBootTest
class SeedDataScriptTest {

    @Autowired
    private DatabaseClient client;

    @Autowired
    private UserRepository users;

    @Autowired
    private ConversationService conversations;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedScriptCreatesRolesAndOwnedConversations() {
        runScript("db/app-data.sql");

        AppUser admin = users.findByUsername("admin").block();
        assertThat(admin).isNotNull();
        assertThat(admin.role()).isEqualTo(Roles.ADMIN);
        assertThat(admin.isAdmin()).isTrue();

        assertThat(users.findByUsername("alice").block().role()).isEqualTo(Roles.USER);
        assertThat(users.findByUsername("bob").block().role()).isEqualTo(Roles.USER);

        // The bcrypt hashes must match the passwords documented in app-data.sql, docs/deploy.md and
        // bootstrap.sh. A wrong hash would only surface on a freshly deployed server, as "cannot log
        // in as admin", which is exactly the demo the seed data exists to enable.
        assertThat(passwordEncoder.matches("admin123", admin.passwordHash())).isTrue();
        assertThat(passwordEncoder.matches("alice123", users.findByUsername("alice").block().passwordHash()))
                .isTrue();
        assertThat(passwordEncoder.matches("bob123", users.findByUsername("bob").block().passwordHash()))
                .isTrue();

        // A regular user only sees the conversations it owns.
        assertThat(ids(conversations.list("seed-user-alice", false).collectList().block()))
                .containsExactlyInAnyOrder("seed-conv-alice-1", "seed-conv-alice-2");
        assertThat(ids(conversations.list("seed-user-bob", false).collectList().block()))
                .containsExactly("seed-conv-bob-1");

        // An admin sees every seeded conversation, whoever owns it.
        assertThat(ids(conversations.list("seed-user-admin", true).collectList().block()))
                .contains(
                        "seed-conv-alice-1",
                        "seed-conv-alice-2",
                        "seed-conv-bob-1",
                        "seed-conv-admin-1");

        // The seeded exchange is readable by its owner.
        assertThat(
                        conversations
                                .messages("seed-conv-alice-1", "seed-user-alice", false)
                                .collectList()
                                .block())
                .hasSize(2);
    }

    private static java.util.List<String> ids(java.util.List<Conversation> rows) {
        return rows.stream().map(Conversation::id).toList();
    }

    private void runScript(String location) {
        String sql;
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + location, ex);
        }
        String body =
                sql.lines()
                        .filter(line -> !line.stripLeading().startsWith("--"))
                        .collect(Collectors.joining("\n"));
        for (String statement : body.split(";")) {
            String trimmed = statement.strip();
            if (!trimmed.isEmpty()) {
                client.sql(trimmed).then().block();
            }
        }
    }
}
