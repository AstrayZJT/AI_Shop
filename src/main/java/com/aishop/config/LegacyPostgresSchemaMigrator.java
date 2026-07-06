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
            "product_reviews",
            "products",
            "promotion_campaigns");

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

        ensurePromotionSchema();

        int checkedTables = 0;
        for (String tableName : AUDITED_TABLES) {
            if (!tableExists(tableName)) {
                continue;
            }
            ensureAuditColumns(tableName);
            checkedTables++;
        }

        ensureOrderStatusConstraint();
        ensureOrderPromotionFields();
        ensureAssistantSessionServiceStatus();
        ensureAssistantSessionOpsFields();

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
                    'PENDING_PAYMENT',
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

    private void ensureOrderPromotionFields() {
        if (!tableExists("orders")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS original_amount NUMERIC(12,2)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_code VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_title VARCHAR(128)");
        jdbcTemplate.update("UPDATE orders SET original_amount = COALESCE(original_amount, total_amount) WHERE original_amount IS NULL");
        jdbcTemplate.update("UPDATE orders SET discount_amount = COALESCE(discount_amount, 0) WHERE discount_amount IS NULL");
    }

    private void ensurePromotionSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS promotion_campaigns (
                    id BIGSERIAL PRIMARY KEY,
                    code VARCHAR(32) NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    description VARCHAR(1000),
                    discount_type VARCHAR(16) NOT NULL,
                    discount_value NUMERIC(12,2) NOT NULL,
                    min_order_amount NUMERIC(12,2),
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    expires_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS code VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS title VARCHAR(128)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS description VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS discount_type VARCHAR(16)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS min_order_amount NUMERIC(12,2)");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS active BOOLEAN");
        jdbcTemplate.execute("ALTER TABLE promotion_campaigns ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.update("UPDATE promotion_campaigns SET active = COALESCE(active, TRUE) WHERE active IS NULL");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_promotion_campaigns_code ON promotion_campaigns (code)");
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

    private void ensureAssistantSessionOpsFields() {
        if (!tableExists("assistant_sessions")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS assigned_admin_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS first_support_reply_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS last_customer_message_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS last_support_message_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS support_unread_count BIGINT");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ADD COLUMN IF NOT EXISTS customer_unread_count BIGINT");
        jdbcTemplate.update(
                "UPDATE assistant_sessions SET support_unread_count = COALESCE(support_unread_count, 0), customer_unread_count = COALESCE(customer_unread_count, 0) WHERE support_unread_count IS NULL OR customer_unread_count IS NULL");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ALTER COLUMN support_unread_count SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE assistant_sessions ALTER COLUMN customer_unread_count SET NOT NULL");
    }
}
