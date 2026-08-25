package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.NativeQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Small JPA-native gateway used while the assistant state machine keeps its
 * explicit SQL CAS and lock ordering. Flyway remains the schema authority;
 * all assistant reads and writes execute through the managed EntityManager.
 */
@Repository
@Profile("persistence")
public class AssistantJpaGateway {
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public AssistantJpaGateway(EntityManager entityManager, EntityManagerFactory entityManagerFactory) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManagerFactory;
    }

    public int update(String sql, Parameters parameters) {
        Query query = entityManager.createNativeQuery(sql);
        bind(query, parameters);
        try {
            return query.executeUpdate();
        } catch (RuntimeException error) {
            if (isDuplicateKey(error)) {
                throw new DuplicateKeyException("Assistant persistence duplicate key", error);
            }
            throw error;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> query(String sql, Parameters parameters, RowMapper<T> mapper) {
        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        bind(query, parameters);
        List<Tuple> tuples = query.getResultList();
        java.util.ArrayList<T> mapped = new java.util.ArrayList<>(tuples.size());
        for (int index = 0; index < tuples.size(); index++) {
            mapped.add(mapper.map(new JpaRow(tuples.get(index)), index));
        }
        return mapped;
    }

    public <T> T queryForObject(String sql, Parameters parameters, Class<T> targetType) {
        Query query = entityManager.createNativeQuery(sql);
        bind(query, parameters);
        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) {
            throw new IllegalStateException("Expected one assistant persistence row but found " + rows.size());
        }
        Object value = rows.get(0);
        if (value instanceof Object[] values && values.length == 1) value = values[0];
        return convert(value, targetType);
    }

    public boolean isPostgres() {
        try {
            SessionFactoryImplementor factory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            String dialect = factory.getJdbcServices().getDialect().getClass().getName();
            return dialect.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (RuntimeException ignored) {
            return entityManagerFactory.getProperties().values().stream()
                    .map(String::valueOf)
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("postgres"));
        }
    }

    private static void bind(Query query, Parameters parameters) {
        if (parameters == null) return;
        for (Map.Entry<String, Object> parameter : parameters.values().entrySet()) {
            Object value = parameter.getValue();
            if (value instanceof Collection<?> collection) {
                query.unwrap(NativeQuery.class).setParameterList(parameter.getKey(), collection);
            } else {
                query.setParameter(parameter.getKey(), value);
            }
        }
    }

    private static boolean isDuplicateKey(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())) return true;
            if (current instanceof SQLIntegrityConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())) return true;
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) return true;
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("duplicate key") || normalized.contains("unique index or primary key violation")) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return (T) value;
        if (targetType == Integer.class && value instanceof Number number) return (T) Integer.valueOf(number.intValue());
        if (targetType == Long.class && value instanceof Number number) return (T) Long.valueOf(number.longValue());
        if (targetType == String.class) return (T) String.valueOf(value);
        if (targetType == UUID.class) {
            if (value instanceof byte[] bytes && bytes.length == 16) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                return (T) new UUID(buffer.getLong(), buffer.getLong());
            }
            return (T) UUID.fromString(String.valueOf(value));
        }
        if (targetType == LocalDate.class) {
            if (value instanceof Date date) return (T) date.toLocalDate();
            if (value instanceof Timestamp timestamp) return (T) timestamp.toLocalDateTime().toLocalDate();
            return (T) LocalDate.parse(String.valueOf(value));
        }
        if (targetType == BigInteger.class && value instanceof Number number) return (T) BigInteger.valueOf(number.longValue());
        if (targetType == BigDecimal.class && value instanceof Number number) return (T) BigDecimal.valueOf(number.doubleValue());
        throw new IllegalArgumentException("Cannot convert assistant persistence value to " + targetType.getName());
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(JpaRow row, int rowNumber);
    }

    public static final class Parameters {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Parameters() { }

        public Parameters(String name, Object value) {
            addValue(name, value);
        }

        public Parameters addValue(String name, Object value) {
            values.put(name, value);
            return this;
        }

        public Object getValue(String name) {
            return values.get(name);
        }

        private Map<String, Object> values() {
            return values;
        }
    }

    public static final class JpaRow {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private boolean lastValueWasNull;

        private JpaRow(Tuple tuple) {
            for (TupleElement<?> element : tuple.getElements()) {
                String alias = element.getAlias();
                if (alias != null) values.put(alias.toLowerCase(Locale.ROOT), tuple.get(element));
            }
        }

        public String getString(String column) {
            Object value = value(column);
            if (value instanceof byte[] bytes && bytes.length == 16) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                return new UUID(buffer.getLong(), buffer.getLong()).toString();
            }
            return value == null ? null : String.valueOf(value);
        }

        public boolean getBoolean(String column) {
            Object value = value(column);
            if (value == null) return false;
            if (value instanceof Boolean booleanValue) return booleanValue;
            if (value instanceof Number number) return number.intValue() != 0;
            String text = String.valueOf(value);
            return "true".equalsIgnoreCase(text) || "t".equalsIgnoreCase(text) || "1".equals(text);
        }

        public int getInt(String column) {
            Object value = value(column);
            if (value == null) return 0;
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        }

        public long getLong(String column) {
            Object value = value(column);
            if (value == null) return 0L;
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        }

        public Timestamp getTimestamp(String column) {
            Object value = value(column);
            if (value == null) return null;
            if (value instanceof Timestamp timestamp) return timestamp;
            if (value instanceof Instant instant) return Timestamp.from(instant);
            if (value instanceof OffsetDateTime offsetDateTime) return Timestamp.from(offsetDateTime.toInstant());
            if (value instanceof LocalDateTime localDateTime) return Timestamp.valueOf(localDateTime);
            if (value instanceof Date date) return Timestamp.from(date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
            throw new IllegalArgumentException("Cannot convert assistant persistence column " + column + " to timestamp");
        }

        public <T> T getObject(String column, Class<T> targetType) {
            return convert(value(column), targetType);
        }

        public boolean wasNull() {
            return lastValueWasNull;
        }

        private Object value(String column) {
            String normalized = column.toLowerCase(Locale.ROOT);
            if (!values.containsKey(normalized)) {
                throw new IllegalArgumentException("Assistant persistence result is missing column " + column);
            }
            Object value = values.get(normalized);
            lastValueWasNull = value == null;
            return value;
        }
    }
}
