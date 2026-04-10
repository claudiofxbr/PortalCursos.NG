import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Properties;

public class CheckMECSchema {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech/neondb";
        Properties props = new Properties();
        props.setProperty("user", "neondb_owner");
        props.setProperty("password", "npg_68oNjuEsZGAi");
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");

        System.out.println("--- DIAGNÓSTICO SCHEMA MEC 2026 ---");
        try (Connection conn = DriverManager.getConnection(url, props)) {
            java.sql.Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM courses LIMIT 1");
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            System.out.println("Colunas encontradas na tabela 'courses':");
            for (int i = 1; i <= columnCount; i++) {
                System.out.println("- " + metaData.getColumnName(i) + " (" + metaData.getColumnTypeName(i) + ")");
            }
        } catch (Exception e) {
            System.err.println("ERRO: " + e.getMessage());
        }
    }
}
