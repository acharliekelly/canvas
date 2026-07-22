package org.canvas.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigrationFromEmptyTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("canvas")
            .withUsername("canvas")
            .withPassword("canvas-test-password");

    @Test
    void allMigrationsBuildTheExpectedSchemaFromAnEmptyDatabase() throws Exception {
        Flyway flyway = flyway();
        flyway.clean();
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(8);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            assertThat(count(statement, """
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('artworks', 'descriptions', 'description_revisions',
                          'caption_jobs', 'publications', 'published_descriptions', 'generated_assets')
                    """)).isEqualTo(7);
            assertThat(count(statement, """
                    SELECT count(*) FROM flyway_schema_history
                    WHERE success
                    """)).isEqualTo(8);
            assertThat(count(statement, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND ((table_name = 'publications' AND column_name = 'qr_asset_id')
                        OR (table_name = 'published_descriptions' AND column_name = 'audio_asset_id'))
                    """)).isEqualTo(2);
            assertThat(count(statement, """
                    SELECT count(*) FROM pg_constraint
                    WHERE conrelid = 'publications'::regclass
                      AND conname = 'publications_artwork_content_unique'
                    """)).isZero();
        }
    }

    @Test
    void v8BackfillsOnlyUnambiguousHistoricalAssetAssociations() throws Exception {
        Flyway flyway = flyway("7");
        flyway.clean();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO artworks (id, title, credit, media_type, byte_size, object_key,
                        lifecycle_status, public_slug, version, created_at, updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000001', 'Study', 'Artist', 'image/png',
                        10, 'artworks/study', 'PUBLISHED', 'study', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO descriptions (id, artwork_id, source, display_order, version,
                        created_at, updated_at)
                    VALUES
                      ('00000000-0000-0000-0000-000000000101',
                       '00000000-0000-0000-0000-000000000001', 'MANUAL', 0, 0,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000102',
                       '00000000-0000-0000-0000-000000000001', 'MANUAL', 1, 0,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO description_revisions (id, description_id, label, text, state,
                        approved_by, approved_at, created_at, updated_at)
                    VALUES
                      ('00000000-0000-0000-0000-000000000201',
                       '00000000-0000-0000-0000-000000000101', 'Objective', 'Unique audio.',
                       'APPROVED', 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000202',
                       '00000000-0000-0000-0000-000000000102', 'Subjective', 'Ambiguous audio.',
                       'APPROVED', 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO publications (id, artwork_id, publication_version, content_hash,
                        current_artwork_id, snapshot_title, snapshot_credit, snapshot_image_object_key,
                        snapshot_image_media_type, snapshot_image_byte_size, published_by, published_at)
                    VALUES
                      ('00000000-0000-0000-0000-000000000301',
                       '00000000-0000-0000-0000-000000000001', 1,
                       'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', NULL,
                       'Study', 'Artist', 'artworks/study', 'image/png', 10,
                       '00000000-0000-0000-0000-000000000601', CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000302',
                       '00000000-0000-0000-0000-000000000001', 2,
                       'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
                       '00000000-0000-0000-0000-000000000001', 'Study', 'Artist',
                       'artworks/study', 'image/png', 10,
                       '00000000-0000-0000-0000-000000000601', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO published_descriptions (id, publication_id, approved_revision_id,
                        display_order, label, text)
                    VALUES
                      ('00000000-0000-0000-0000-000000000401',
                       '00000000-0000-0000-0000-000000000301',
                       '00000000-0000-0000-0000-000000000201', 0, 'Objective', 'Unique audio.'),
                      ('00000000-0000-0000-0000-000000000402',
                       '00000000-0000-0000-0000-000000000301',
                       '00000000-0000-0000-0000-000000000202', 1, 'Subjective', 'Ambiguous audio.')
                    """);
            statement.executeUpdate("""
                    INSERT INTO generated_assets (id, kind, input_key, media_type, byte_size,
                        object_key, generator, source_revision_id, source_publication_id,
                        created_at, updated_at)
                    VALUES
                      ('00000000-0000-0000-0000-000000000501', 'AUDIO',
                       'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                       'audio/wav', 10, 'generated/audio/unique.wav', 'audio-v1',
                       '00000000-0000-0000-0000-000000000201', NULL,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000502', 'AUDIO',
                       'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                       'audio/wav', 10, 'generated/audio/ambiguous-1.wav', 'audio-v1',
                       '00000000-0000-0000-0000-000000000202', NULL,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000503', 'AUDIO',
                       'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                       'audio/wav', 10, 'generated/audio/ambiguous-2.wav', 'audio-v2',
                       '00000000-0000-0000-0000-000000000202', NULL,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                      ('00000000-0000-0000-0000-000000000504', 'QR_CODE',
                       'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
                       'image/png', 10, 'generated/qr/study.png', 'qr-v1', NULL,
                       '00000000-0000-0000-0000-000000000301', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
        }

        var migrated = flyway().migrate();
        assertThat(migrated.migrationsExecuted).isOne();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            assertThat(value(statement, """
                    SELECT audio_asset_id::text FROM published_descriptions
                    WHERE id = '00000000-0000-0000-0000-000000000401'
                    """)).isEqualTo("00000000-0000-0000-0000-000000000501");
            assertThat(value(statement, """
                    SELECT audio_asset_id::text FROM published_descriptions
                    WHERE id = '00000000-0000-0000-0000-000000000402'
                    """)).isNull();
            assertThat(value(statement, """
                    SELECT qr_asset_id::text FROM publications
                    WHERE id = '00000000-0000-0000-0000-000000000301'
                    """)).isEqualTo("00000000-0000-0000-0000-000000000504");
            assertThat(value(statement, """
                    SELECT qr_asset_id::text FROM publications
                    WHERE id = '00000000-0000-0000-0000-000000000302'
                    """)).isNull();
            assertThatThrownBy(() -> statement.executeUpdate("""
                    DELETE FROM generated_assets
                    WHERE id = '00000000-0000-0000-0000-000000000501'
                    """)).isInstanceOf(SQLException.class);
            assertThat(statement.executeUpdate("""
                    INSERT INTO publications (id, artwork_id, publication_version, content_hash,
                        snapshot_title, snapshot_credit, snapshot_image_object_key,
                        snapshot_image_media_type, snapshot_image_byte_size, published_by, published_at)
                    VALUES ('00000000-0000-0000-0000-000000000303',
                        '00000000-0000-0000-0000-000000000001', 3,
                        'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
                        'Study', 'Artist', 'artworks/study', 'image/png', 10,
                        '00000000-0000-0000-0000-000000000601', CURRENT_TIMESTAMP)
                    """)).isOne();
        }
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static String value(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static int count(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
