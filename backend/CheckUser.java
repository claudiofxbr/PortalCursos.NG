import java.sql.*;

public class CheckUser {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require";
        String user = "neondb_owner";
        String pass = "npg_68oNjuEsZGAi";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("LOG: Conectado ao Neon.");
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT username, email FROM users");
                while (rs.next()) {
                    System.out.println("USER_FOUND: " + rs.getString("username") + " | " + rs.getString("email"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
