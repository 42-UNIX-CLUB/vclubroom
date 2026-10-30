package pt.vclubroom.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pt.vclubroom.notification.application.EmailDeliveryException;
import pt.vclubroom.notification.application.OutgoingEmail;

/** The SMTP adapter against a real Mailpit (same image as compose.dev.yaml), without starting Spring. */
@Testcontainers
class SmtpEmailSenderTests {

    @Container
    static final GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.31")
        .withExposedPorts(1025, 8025)
        .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    private static final HttpClient http = HttpClient.newHttpClient();

    private static String get(String path) throws Exception {
        URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
        return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static SmtpEmailSender sender(int port, String replyTo) {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(mailpit.getHost());
        mail.setPort(port);
        return new SmtpEmailSender(mail, new AppMailProperties("smtp", "Virtual Clubroom <no-reply@clubroom.test>", replyTo));
    }

    @Test
    void sendsPlainTextFromTheConfiguredAddress() throws Exception {
        sender(mailpit.getMappedPort(1025), "team@clubroom.test")
            .send(new OutgoingEmail("ada@example.test", "[Virtual Clubroom] Échecs is approved", "Open: http://localhost/c/echecs"));

        String list = get("/api/v1/messages");
        assertThat(JsonPath.<List<String>>read(list, "$.messages[*].Subject")).contains("[Virtual Clubroom] Échecs is approved");
        String id = JsonPath.read(list, "$.messages[0].ID");
        String message = get("/api/v1/message/" + id);
        assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("no-reply@clubroom.test");
        assertThat(JsonPath.<String>read(message, "$.From.Name")).isEqualTo("Virtual Clubroom");
        assertThat(JsonPath.<String>read(message, "$.To[0].Address")).isEqualTo("ada@example.test");
        assertThat(JsonPath.<String>read(message, "$.ReplyTo[0].Address")).isEqualTo("team@clubroom.test");
        assertThat(JsonPath.<String>read(message, "$.Text")).contains("Open: http://localhost/c/echecs");
    }

    @Test
    void anEmailCanComeFromItsOwnAddress() throws Exception {
        sender(mailpit.getMappedPort(1025), null).send(new OutgoingEmail("bob@example.test", "[Virtual Clubroom Support] T-17",
            "Thanks", "Virtual Clubroom Support <support@clubroom.test>"));

        String list = get("/api/v1/search?query=" + URLEncoder.encode("to:bob@example.test", StandardCharsets.UTF_8));
        String id = JsonPath.read(list, "$.messages[0].ID");
        String message = get("/api/v1/message/" + id);
        assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("support@clubroom.test");
        assertThat(JsonPath.<String>read(message, "$.From.Name")).isEqualTo("Virtual Clubroom Support");
    }

    @Test
    void anUnreachableServerIsADeliveryFailure() {
        assertThatThrownBy(() -> sender(mailpit.getMappedPort(8025), null)       // HTTP, not SMTP
            .send(new OutgoingEmail("ada@example.test", "Hello", "Hi")))
            .isInstanceOf(EmailDeliveryException.class)
            .hasMessageStartingWith("SMTP delivery failed");
    }
}
