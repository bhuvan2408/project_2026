import java.sql.*;

/** Creates the SQLite database file (securebank.db) and its tables. */
public class Database {
    private static final String URL = "jdbc:sqlite:securebank.db";

    public static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA busy_timeout = 5000"); }
        return c;
    }

    public static void init() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS accounts ("
                + "account_no INTEGER PRIMARY KEY,"
                + "type TEXT NOT NULL CHECK(type IN ('Savings','Current')),"
                + "holder_name TEXT NOT NULL,"
                + "balance REAL NOT NULL,"
                + "pin_hash TEXT NOT NULL,"
                + "salt TEXT NOT NULL,"
                + "failed_attempts INTEGER NOT NULL DEFAULT 0,"
                + "locked INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS transactions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "account_no INTEGER NOT NULL REFERENCES accounts(account_no),"
                + "type TEXT NOT NULL,"
                + "amount REAL NOT NULL,"
                + "balance_after REAL NOT NULL,"
                + "note TEXT,"
                + "created_at TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_txn_acc ON transactions(account_no, id)");
        }
    }
}
