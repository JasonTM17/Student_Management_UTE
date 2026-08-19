package io.campuscore.thesis.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HealthControllerTest {

    @Test
    void readinessRequiresTheSharedHealthKeyBeforeTouchingDependencies() {
        DataSource dataSource = mock(DataSource.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HealthController controller = new HealthController("secret", dataSource, redis);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.readiness("wrong"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(dataSource, redis);
    }

    @Test
    void readinessChecksPostgresAndRedis() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection sqlConnection = mock(Connection.class);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        when(dataSource.getConnection()).thenReturn(sqlConnection);
        when(sqlConnection.isValid(2)).thenReturn(true);
        when(redis.getConnectionFactory()).thenReturn(redisFactory);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        HealthController controller = new HealthController("secret", dataSource, redis);

        assertEquals("ready", controller.readiness("secret").get("status"));
        verify(sqlConnection).isValid(2);
        verify(redisConnection).ping();
    }

    @Test
    void readinessReturnsServiceUnavailableWhenPostgresIsNotValid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection sqlConnection = mock(Connection.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        when(dataSource.getConnection()).thenReturn(sqlConnection);
        when(sqlConnection.isValid(2)).thenReturn(false);

        HealthController controller = new HealthController("secret", dataSource, redis);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.readiness("secret"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }
}
