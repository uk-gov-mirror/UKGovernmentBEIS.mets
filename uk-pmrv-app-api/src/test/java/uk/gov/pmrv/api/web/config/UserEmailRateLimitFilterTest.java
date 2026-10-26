package uk.gov.pmrv.api.web.config;

import com.giffing.bucket4j.spring.boot.starter.config.cache.Bucket4jCacheConfiguration;
import com.giffing.bucket4j.spring.boot.starter.config.filter.servlet.Bucket4JAutoConfigurationServletFilter;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.pmrv.api.user.core.service.UserResetPasswordService;
import uk.gov.pmrv.api.user.operator.service.OperatorUserAcceptInvitationService;
import uk.gov.pmrv.api.user.operator.service.OperatorUserActivationService;
import uk.gov.pmrv.api.user.operator.service.OperatorUserRegistrationService;
import uk.gov.pmrv.api.user.operator.service.OperatorUserTokenVerificationService;
import uk.gov.pmrv.api.web.controller.user.ForgotPasswordController;
import uk.gov.pmrv.api.web.controller.user.OperatorUserRegistrationController;

import javax.cache.CacheManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies the per client rate limiting of the user email endpoints, as configured in the application properties.
 */
@SpringBootTest(
        classes = UserEmailRateLimitFilterTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bucket4j.enabled=true")
@TestPropertySource(locations = "file:src/main/resources/application.properties")
class UserEmailRateLimitFilterTest {

    private static final String RESET_PASSWORD_EMAIL_PATH = "/v1.0/users/forgot-password/reset-password-email";
    private static final String VERIFICATION_EMAIL_PATH = "/v1.0/operator-users/registration/verification-email";
    private static final String EMAIL_BODY = "{\"email\":\"user@example.com\"}";

    private static final int OK = HttpStatus.OK.value();
    private static final int NO_CONTENT = HttpStatus.NO_CONTENT.value();
    private static final int TOO_MANY_REQUESTS = HttpStatus.TOO_MANY_REQUESTS.value();

    @Configuration
    @EnableCaching
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            CacheAutoConfiguration.class,
            Bucket4jCacheConfiguration.class,
            Bucket4JAutoConfigurationServletFilter.class
    })
    @Import({RateLimitCacheConfig.class, ForgotPasswordController.class, OperatorUserRegistrationController.class})
    static class TestConfig {
    }

    @MockitoBean
    private UserResetPasswordService userResetPasswordService;

    @MockitoBean
    private OperatorUserRegistrationService operatorUserRegistrationService;

    @MockitoBean
    private OperatorUserTokenVerificationService operatorUserTokenVerificationService;

    @MockitoBean
    private OperatorUserActivationService operatorUserActivationService;

    @MockitoBean
    private OperatorUserAcceptInvitationService operatorUserAcceptInvitationService;

    @Autowired
    private CacheManager cacheManager;

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void rejects_requests_over_the_limit_per_client() throws Exception {
        String clientIp = "198.51.100.1";

        for (int i = 0; i < 5; i++) {
            assertThat(post(RESET_PASSWORD_EMAIL_PATH, clientIp).statusCode()).isEqualTo(NO_CONTENT);
        }
        HttpResponse<String> rejected = post(RESET_PASSWORD_EMAIL_PATH, clientIp);

        assertThat(rejected.statusCode()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(rejected.body())
                .isEqualTo("{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Too Many Requests\",\"data\":[]}");
        assertThat(rejected.headers().firstValue("X-Rate-Limit-Retry-After-Seconds")).isPresent();
        verify(userResetPasswordService, times(5)).sendResetPasswordEmail(anyString());
        // the client is identified by the address resolved from X-Forwarded-For
        assertThat(cacheManager.getCache(RateLimitCacheConfig.CLIENT_BUCKETS_CACHE).containsKey(".*-" + clientIp))
                .isTrue();
    }

    @Test
    void shares_the_limit_between_the_email_endpoints() throws Exception {
        String clientIp = "198.51.100.2";

        for (int i = 0; i < 3; i++) {
            assertThat(post(RESET_PASSWORD_EMAIL_PATH, clientIp).statusCode()).isEqualTo(NO_CONTENT);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(post(VERIFICATION_EMAIL_PATH, clientIp).statusCode()).isEqualTo(NO_CONTENT);
        }

        assertThat(post(VERIFICATION_EMAIL_PATH, clientIp).statusCode()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(post(RESET_PASSWORD_EMAIL_PATH, clientIp).statusCode()).isEqualTo(TOO_MANY_REQUESTS);
    }

    @Test
    void limits_each_client_separately() throws Exception {
        for (int i = 0; i < 6; i++) {
            post(RESET_PASSWORD_EMAIL_PATH, "198.51.100.3");
        }

        assertThat(post(RESET_PASSWORD_EMAIL_PATH, "198.51.100.4").statusCode()).isEqualTo(NO_CONTENT);
    }

    @Test
    void limits_url_encoded_variants_of_the_path() throws Exception {
        String clientIp = "198.51.100.5";
        String encodedPath = "/v1.0/users/forgot-password/r%65set-password-email";

        for (int i = 0; i < 5; i++) {
            assertThat(post(encodedPath, clientIp).statusCode()).isEqualTo(NO_CONTENT);
        }

        assertThat(post(encodedPath, clientIp).statusCode()).isEqualTo(TOO_MANY_REQUESTS);
    }

    @Test
    void does_not_limit_other_endpoints() throws Exception {
        String tokenBody = "{\"token\":\"token\"}";

        for (int i = 0; i < 10; i++) {
            assertThat(post("/v1.0/users/forgot-password/token-verification", "198.51.100.6", tokenBody).statusCode())
                    .isEqualTo(OK);
        }
    }

    @Test
    void does_not_limit_other_methods() throws Exception {
        String clientIp = "198.51.100.7";

        for (int i = 0; i < 10; i++) {
            HttpRequest options = HttpRequest.newBuilder(uri(RESET_PASSWORD_EMAIL_PATH))
                    .header("X-Forwarded-For", clientIp)
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();
            assertThat(httpClient.send(options, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(OK);
        }

        assertThat(post(RESET_PASSWORD_EMAIL_PATH, clientIp).statusCode()).isEqualTo(NO_CONTENT);
    }

    @Test
    void client_buckets_cache_is_bounded() {
        CaffeineConfiguration<?, ?> configuration = cacheManager.getCache(RateLimitCacheConfig.CLIENT_BUCKETS_CACHE)
                .getConfiguration(CaffeineConfiguration.class);

        assertThat(configuration.getMaximumSize()).hasValue(RateLimitCacheConfig.CLIENT_BUCKETS_MAX_SIZE);
        assertThat(configuration.getExpireAfterAccess())
                .hasValue(RateLimitCacheConfig.CLIENT_BUCKETS_EXPIRE_AFTER_ACCESS.toNanos());
    }

    private HttpResponse<String> post(String path, String clientIp) throws IOException, InterruptedException {
        return post(path, clientIp, EMAIL_BODY);
    }

    private HttpResponse<String> post(String path, String clientIp, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + contextPath + path);
    }
}
