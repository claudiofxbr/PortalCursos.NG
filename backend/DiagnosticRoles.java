import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Properties;

public class DiagnosticRoles {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech/neondb";
        Properties props = new Properties();
        props.setProperty("user", "neondb_owner");
        props.setProperty("password", "npg_68oNjuEsZGAi");
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");

        System.out.println("--- DIAGNOSTICO TABELA ROLES ---");
        try (Connection conn = DriverManager.getConnection(url, props)) {
            java.sql.Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name FROM roles ORDER BY id ASC");
            while (rs.next()) {
                System.out.println("ID: " + rs.getLong(1) + " | Name: " + rs.getString(2));
            }
            
            // Check next val of sequence if it exists
            try {
                ResultSet srs = stmt.executeQuery("SELECT last_value FROM roles_id_seq");
                if (srs.next()) {
                    System.out.println("Sequence last_value: " + srs.getLong(1));
                }
            } catch (Exception e) {
                System.out.println("Não foi possível consultar roles_id_seq: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("ERRO: " + e.getMessage());
        }
    }
}
