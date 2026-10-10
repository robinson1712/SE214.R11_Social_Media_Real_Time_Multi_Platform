import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Apply the repository schema setup in one transaction without logging secrets. */
class InitializeSupabase {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("INIT_SUPABASE_USER"));
        properties.setProperty("password", System.getenv("INIT_SUPABASE_PASSWORD"));
        properties.setProperty("connectTimeout", "8");
        properties.setProperty("socketTimeout", "45");
        DriverManager.setLoginTimeout(8);
        try (Connection connection = DriverManager.getConnection(System.getenv("INIT_SUPABASE_URL"), properties)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(30);
                statement.execute(Files.readString(Path.of(args[0])));
                connection.commit();
                System.out.println("SupabaseSchemas=PASS schema setup committed");
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            System.out.println("SupabaseSchemas=FAIL SQLSTATE=" + failure.getSQLState());
            System.exit(1);
        } catch (Exception failure) {
            System.out.println("SupabaseSchemas=FAIL " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
