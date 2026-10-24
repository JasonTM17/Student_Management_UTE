package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.mail.service.EmailServiceImpl;
import io.campuscore.restfulapi.exception.DomainException;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** Real EmailServiceImpl, Thymeleaf and JavaMailSender submitting to an owned IPv4 loopback SMTP server. */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_result_publication_smtp;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "mail.enabled=true",
        "mail.from=sender@fixture.test",
        "mail.sender-name=CampusUTE - Kiểm thử thư",
        "spring.mail.host=127.0.0.1",
        "spring.mail.username=",
        "spring.mail.password=test-only-loopback-secret",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "spring.mail.properties.mail.smtp.connectiontimeout=5000",
        "spring.mail.properties.mail.smtp.timeout=5000",
        "spring.mail.properties.mail.smtp.writetimeout=5000"
})
class ThesisResultPublicationSmtpTest extends ThesisPublicationFixture {

    private static final LoopbackSmtp SMTP = LoopbackSmtp.start();

    @Autowired
    private EmailService email;
    @Autowired
    private JavaMailSender sender;

    @DynamicPropertySource
    static void ownEphemeralSmtpPort(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.port", SMTP::port);
    }

    @BeforeEach
    void resetSmtpCapture() {
        SMTP.reset(false);
        assertThat(AopUtils.getTargetClass(email)).isEqualTo(EmailServiceImpl.class);
        assertThat(sender).isInstanceOf(JavaMailSenderImpl.class);
        assertThat(((JavaMailSenderImpl) sender).getHost()).isEqualTo("127.0.0.1");
        assertThat(((JavaMailSenderImpl) sender).getPort()).isEqualTo(SMTP.port());
    }

    @AfterAll
    static void closeOwnedSmtpServer() throws Exception {
        SMTP.close();
        System.out.printf("RESULT_MAIL_SMTP_CLEANUP port=%d closed=true workerAlive=false%n", SMTP.port());
    }

    @Test
    void committedPublicationProducesIndividualEscapedUtf8PlainAndHtmlMessages() throws Exception {
        mutations.publishResults(roundId, admin());

        SMTP.assertHealthy();
        assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        assertThat(inboxCount()).isEqualTo(6);
        assertThat(SMTP.messages).hasSize(6);
        assertThat(SMTP.recipientAttempts).containsExactlyInAnyOrderElementsOf(eligibleAddresses());
        for (CapturedMessage captured : SMTP.messages) {
            assertThat(captured.envelopeRecipients()).hasSize(1);
            String recipient = captured.envelopeRecipients().getFirst();
            MimeMessage message = captured.mime();
            assertThat(message.getAllRecipients()).hasSize(1);
            assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                    .isEqualTo(recipient);
            assertThat(message.getRecipients(Message.RecipientType.CC)).isNull();
            assertThat(message.getRecipients(Message.RecipientType.BCC)).isNull();
            assertThat(message.getSubject()).isEqualTo("[CampusUTE] Kết quả khóa luận đã công bố");
            assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("CampusUTE - Kiểm thử thư");

            List<Part> leaves = leafParts(message);
            assertThat(leaves).hasSize(2);
            Part plain = leaves.stream().filter(part -> hasType(part, "text/plain")).findFirst().orElseThrow();
            Part html = leaves.stream().filter(part -> hasType(part, "text/html")).findFirst().orElseThrow();
            assertThat(new ContentType(plain.getContentType()).getParameter("charset")).isEqualToIgnoringCase("UTF-8");
            assertThat(new ContentType(html.getContentType()).getParameter("charset")).isEqualToIgnoringCase("UTF-8");
            String text = (String) plain.getContent();
            String markup = (String) html.getContent();
            int student = Integer.parseInt(recipient.substring("rmv-user-".length(), recipient.indexOf('@')));
            assertThat(text).contains("Kết quả khóa luận đã công bố", "Trần Sinh viên " + student + " ", "Đợt bảo vệ");
            assertThat(markup).contains("&lt;script&gt;mailRoundProbe&lt;/script&gt;", "&lt;img src=x onerror=");
            assertThat(Jsoup.parse(markup).select("script,img[onerror],svg[onload]")).isEmpty();
            assertThat(markup).doesNotContain("<script>mailRoundProbe</script>", "<img src=x onerror=");
            assertThat(Jsoup.parse(markup).select("a[href]").eachAttr("href"))
                    .contains("https://www.campusute.io.vn/dashboard/thesis?roundId=" + roundId);
            for (String body : List.of(text, markup)) {
                assertThat(body).doesNotContain("8.57", "6.13", "9.42", "Đợt khác không nhận thư");
                for (int other = 1; other <= 15; other++) {
                    assertThat(body).doesNotContain(address(other));
                    if (other != student) {
                        assertThat(body).doesNotContain("Sinh viên " + other + " ");
                    }
                }
            }
            System.out.printf("RESULT_MAIL_MIME to=%s envelopeRecipients=1 to=1 cc=0 bcc=0 plain=UTF-8 html=UTF-8 escaped=true grades=0 bytes=%d%n",
                    recipient, captured.raw().length);
        }
        observation("loopback-real-mail", SMTP.messages.size());
    }

    @Test
    void smtpRejectsFirstRecipientButRemainingStudentsStillReceiveMail() {
        SMTP.reset(true);

        mutations.publishResults(roundId, admin());

        SMTP.assertHealthy();
        assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        assertThat(inboxCount()).isEqualTo(6);
        assertThat(SMTP.recipientAttempts).containsExactlyInAnyOrderElementsOf(eligibleAddresses());
        assertThat(SMTP.rejections).hasValue(1);
        assertThat(SMTP.messages).hasSize(5);
        assertThat(SMTP.messages).allSatisfy(message -> assertThat(message.envelopeRecipients()).hasSize(1));
        observation("loopback-first-rcpt-550", SMTP.messages.size());
    }

    @Test
    void actualSmtpSeesNothingBeforeOuterCommitOrAfterRollback() {
        transaction().executeWithoutResult(status -> {
            mutations.publishResults(roundId, admin());
            assertThat(SMTP.recipientAttempts).isEmpty();
            assertThat(SMTP.messages).isEmpty();
            assertThat(inboxCount()).isZero();
            status.setRollbackOnly();
        });

        assertThat(roundStatus()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(inboxCount()).isZero();
        assertThat(SMTP.recipientAttempts).isEmpty();
        assertThat(SMTP.messages).isEmpty();
        SMTP.assertHealthy();
        observation("loopback-outer-rollback", 0);
    }

    @Test
    void disabledMailAndDirectUnpublishedCallMakeNoSmtpConnection() {
        assertThat(notifier.emailResultsPublished(roundId)).isZero();
        mailConfig.setEnabled(false);

        mutations.publishResults(roundId, admin());

        assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        assertThat(inboxCount()).isEqualTo(6);
        assertThat(SMTP.connections).hasValue(0);
        assertThat(SMTP.recipientAttempts).isEmpty();
        assertThat(SMTP.messages).isEmpty();
        SMTP.assertHealthy();
        observation("loopback-mail-disabled", 0);
    }

    @Test
    void repeatedPublicationConflictDoesNotRetryPreviouslySubmittedSmtpMessages() {
        mutations.publishResults(roundId, admin());
        assertThat(SMTP.messages).hasSize(6);
        int connections = SMTP.connections.get();

        assertThatThrownBy(() -> mutations.publishResults(roundId, admin()))
                .isInstanceOfSatisfying(DomainException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(failure.getCode()).isEqualTo("ROUND_STATE_CONFLICT");
                });

        assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        assertThat(inboxCount()).isEqualTo(6);
        assertThat(SMTP.messages).hasSize(6);
        assertThat(SMTP.recipientAttempts).hasSize(6);
        assertThat(SMTP.connections).hasValue(connections);
        SMTP.assertHealthy();
        observation("loopback-retry-409", SMTP.messages.size());
    }

    private static boolean hasType(Part part, String type) {
        try {
            return part.isMimeType(type);
        } catch (Exception exception) {
            throw new AssertionError("Unreadable captured MIME part", exception);
        }
    }

    private static List<Part> leafParts(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            List<Part> result = new ArrayList<>();
            for (int index = 0; index < multipart.getCount(); index++) {
                result.addAll(leafParts(multipart.getBodyPart(index)));
            }
            return result;
        }
        return List.of(part);
    }

    private record CapturedMessage(List<String> envelopeRecipients, byte[] raw) {
        MimeMessage mime() throws Exception {
            return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
        }
    }

    /** Minimal in-process SMTP capture. It cannot relay and accepts only reserved .test envelopes. */
    private static final class LoopbackSmtp implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private final List<Socket> openSockets = new CopyOnWriteArrayList<>();
        private final List<CapturedMessage> messages = new CopyOnWriteArrayList<>();
        private final List<String> recipientAttempts = new CopyOnWriteArrayList<>();
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger recipientOrdinal = new AtomicInteger();
        private final AtomicInteger rejections = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile boolean rejectFirstRecipient;
        private volatile boolean closed;

        static LoopbackSmtp start() {
            try {
                return new LoopbackSmtp();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot create owned loopback SMTP fixture", exception);
            }
        }

        private LoopbackSmtp() throws IOException {
            server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(this::acceptConnections, "thesis-result-loopback-smtp");
            worker.setDaemon(true);
            worker.start();
        }

        int port() {
            return server.getLocalPort();
        }

        void reset(boolean rejectFirst) {
            assertHealthy();
            messages.clear();
            recipientAttempts.clear();
            connections.set(0);
            recipientOrdinal.set(0);
            rejections.set(0);
            rejectFirstRecipient = rejectFirst;
        }

        void assertHealthy() {
            assertThat(failure.get()).as("SMTP capture worker has no protocol or IO failure").isNull();
        }

        private void acceptConnections() {
            while (!closed) {
                try (Socket socket = server.accept()) {
                    connections.incrementAndGet();
                    openSockets.add(socket);
                    socket.setSoTimeout(5000);
                    try {
                        serve(socket);
                    } finally {
                        openSockets.remove(socket);
                    }
                } catch (SocketException exception) {
                    if (!closed) {
                        failure.compareAndSet(null, exception);
                    }
                } catch (Throwable exception) {
                    if (!closed) {
                        failure.compareAndSet(null, exception);
                    }
                }
            }
        }

        private void serve(Socket socket) throws IOException {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            Writer output = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
            reply(output, "220 fixture.test local capture");
            List<String> recipients = new ArrayList<>();
            for (String line; (line = input.readLine()) != null;) {
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO ") || command.startsWith("HELO ")) {
                    reply(output, "250-fixture.test\r\n250 8BITMIME");
                } else if (command.startsWith("MAIL FROM:")) {
                    recipients.clear();
                    reply(output, reservedEnvelope(line) ? "250 sender accepted" : "550 fixture accepts only .test addresses");
                } else if (command.startsWith("RCPT TO:")) {
                    String address = envelopeAddress(line);
                    recipientAttempts.add(address);
                    if (!reservedEnvelope(line)) {
                        reply(output, "550 fixture accepts only .test addresses");
                    } else if (rejectFirstRecipient && recipientOrdinal.incrementAndGet() == 1) {
                        rejections.incrementAndGet();
                        reply(output, "550 deterministic fixture recipient failure");
                    } else {
                        recipients.add(address);
                        reply(output, "250 recipient accepted");
                    }
                } else if (command.equals("DATA")) {
                    if (recipients.isEmpty()) {
                        reply(output, "503 recipient required");
                        continue;
                    }
                    reply(output, "354 send MIME data");
                    StringBuilder raw = new StringBuilder();
                    boolean terminated = false;
                    for (String body; (body = input.readLine()) != null;) {
                        if (body.equals(".")) {
                            terminated = true;
                            break;
                        }
                        raw.append(body.startsWith("..") ? body.substring(1) : body).append("\r\n");
                    }
                    if (!terminated) {
                        throw new IOException("SMTP DATA ended before terminating dot");
                    }
                    messages.add(new CapturedMessage(List.copyOf(recipients), raw.toString().getBytes(StandardCharsets.ISO_8859_1)));
                    reply(output, "250 captured locally");
                } else if (command.equals("QUIT")) {
                    reply(output, "221 closed");
                    return;
                } else if (command.equals("RSET")) {
                    recipients.clear();
                    reply(output, "250 reset");
                } else if (command.equals("NOOP")) {
                    reply(output, "250 ok");
                } else {
                    throw new IOException("Unexpected fixture SMTP command: " + command.split(" ", 2)[0]);
                }
            }
        }

        private static String envelopeAddress(String command) {
            int start = command.indexOf('<');
            int end = command.indexOf('>', start);
            if (start < 0 || end < 0) {
                throw new IllegalArgumentException("Malformed fixture SMTP envelope");
            }
            return command.substring(start + 1, end);
        }

        private static boolean reservedEnvelope(String command) {
            return envelopeAddress(command).toLowerCase(Locale.ROOT).endsWith(".test");
        }

        private static void reply(Writer output, String message) throws IOException {
            output.write(message + "\r\n");
            output.flush();
        }

        @Override
        public void close() throws Exception {
            closed = true;
            server.close();
            for (Socket socket : openSockets) {
                socket.close();
            }
            worker.join(5000);
            assertThat(server.isClosed()).isTrue();
            assertThat(worker.isAlive()).isFalse();
            assertThat(openSockets).isEmpty();
            assertHealthy();
        }
    }
}
