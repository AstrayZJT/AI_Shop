package com.aishop.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacyPostgresSchemaMigrator implements CommandLineRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LegacyPostgresSchemaMigrator.class);

    private static final List<String> AUDITED_TABLES = List.of(
            "app_users",
            "assistant_messages",
            "assistant_sessions",
            "cart_items",
            "carts",
            "knowledge_chunks",
            "knowledge_documents",
            "order_items",
            "orders",
            "pending_order_drafts",
            "product_categories",
            "products");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public LegacyPostgresSchemaMigrator(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!isPostgres()) {
            return;
        }

        int checkedTables = 0;
        for (String tableName : AUDITED_TABLES) {
            if (!tableExists(tableName)) {
                continue;
            }
            ensureAuditColumns(tableName);
            checkedTables++;
        }

        ensureOrderStatusConstraint();
        ensureAssistantSessionServiceStatus();

        log.info("Checked PostgreSQL audit columns for {} tables", checkedTables);
    }

    private boolean isPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        }
    }

    private boolean tableExists(String tableName) {
        String tableReference = "public." + tableName;
        return jdbcTemplate.queryForObject("select to_regclass(?)", String.class, tableReference) != null;
    }

    private void ensureAuditColumns(String tableName) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE created_at IS NULL");
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL");
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN created_at SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN updated_at SET NOT NULL");
    }

    private void ensureOrderStatusConstraint() {
        if (!tableExists("orders")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
        jdbcTemplate.execute("""
                ALTER TABLE orders
                ADD CONSTRAINT orders_status_check
                CHECK (status IN (
                    'DRAFT',
                    'PENDING_CONFIRMATION',
                    'CONFIRMED',
                    'PROCESSING',
                    'SHIPPED',
                    'COMPLETED',
                    'REFUND_REQUESTED',
                    'REFUNDED',
                    'CANCELLED'
                ))
                """);
    }

    private void ensureAssistantSessionServiceStatus() {
        if (!tableExists("assistant_sessions")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS service_status VARCHAR(32)");
        jdbcTemplate.update(
                "UPDATE assistant_sessions SET service_status = COALESCE(NULLIF(TRIM(service_status), ''), 'ACTIVE') WHERE service_status IS NULL OR TRIM(service_status) = ''");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ALTER COLUMN service_status SET NOT NULL");
    }
}
