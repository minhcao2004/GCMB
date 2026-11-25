import java.sql.*;
import java.nio.file.*;
public class MigrationCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection(System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/gcmb"), System.getenv().getOrDefault("DB_USERNAME", "gcmb_dev"), System.getenv("DB_PASSWORD")); Statement s = c.createStatement()) {
      boolean rows = s.execute(Files.readString(Path.of(args[0])));
      if (rows) try (ResultSet r = s.getResultSet()) {
        int n = r.getMetaData().getColumnCount();
        while (r.next()) { for (int i=1;i<=n;i++) System.out.print((i>1?" | ":"")+r.getString(i)); System.out.println(); }
      }
    }
  }
}
