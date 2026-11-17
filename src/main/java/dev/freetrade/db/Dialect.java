package dev.freetrade.db;

/** The few SQL differences between SQLite and PostgreSQL that matter here. */
public enum Dialect {
    SQLITE("INTEGER PRIMARY KEY AUTOINCREMENT", ""),
    POSTGRES("BIGSERIAL PRIMARY KEY", " FOR UPDATE");

    public final String autoIncrementPk;
    public final String forUpdate;

    Dialect(String autoIncrementPk, String forUpdate) {
        this.autoIncrementPk = autoIncrementPk;
        this.forUpdate = forUpdate;
    }
}
