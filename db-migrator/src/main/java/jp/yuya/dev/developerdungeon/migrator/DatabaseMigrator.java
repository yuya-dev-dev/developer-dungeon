package jp.yuya.dev.developerdungeon.migrator;

import org.flywaydb.core.Flyway;

public final class DatabaseMigrator {
    private DatabaseMigrator() { }

    public static void main(String[] args) {
        String url = required("DEVELOPER_DUNGEON_MIGRATION_DB_URL");
        String user = required("DEVELOPER_DUNGEON_MIGRATION_DB_USER");
        String password = required("DEVELOPER_DUNGEON_MIGRATION_DB_PASSWORD");
        Flyway.configure().dataSource(url, user, password).defaultSchema("developer_dungeon").schemas("developer_dungeon").load().migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
