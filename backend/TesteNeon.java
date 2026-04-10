import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class TesteNeon {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://ep-small-shadow-acm4l09l-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0";
        Properties props = new Properties();
        props.setProperty("user", "neondb_owner");
        props.setProperty("password", "npg_68oNjuEsZGAi");

        System.out.println("--- [REPARO V2.1] Atualizando Roles e Removendo Restricoes ---");
        try (Connection conn = DriverManager.getConnection(url, props)) {
            Statement stmt = conn.createStatement();
            
            // 1. Tentar dropar a restrição problematica
            try {
                System.out.println("[REPARO] Drop constraint 'roles_name_check'...");
                stmt.execute("ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check;");
                System.out.println("[OK] Constraint drop successful or not found.");
            } catch (Exception e) {
                System.out.println("[INFO] Error dropping constraint: " + e.getMessage());
            }

            // 2. Corrigir nomes de roles legadas
            int updatedStudent = stmt.executeUpdate("UPDATE roles SET name = 'ROLE_ALUNO' WHERE name = 'ROLE_STUDENT';");
            int updatedTeacher = stmt.executeUpdate("UPDATE roles SET name = 'ROLE_PROFESSOR' WHERE name = 'ROLE_TEACHER';");
            
            System.out.println("[REPARO] ROLE_STUDENT -> ROLE_ALUNO: " + updatedStudent);
            System.out.println("[REPARO] ROLE_TEACHER -> ROLE_PROFESSOR: " + updatedTeacher);

            // 3. Garantir que as outras 10 roles existam
            String[] roles = {
                "ROLE_ROOT_MASTER", "ROLE_ADMIN", "ROLE_SECRETARIA", "ROLE_COORDENADOR", 
                "ROLE_PROFESSOR", "ROLE_MONITOR", "ROLE_BIBLIOTECARIO", "ROLE_ALUNO", 
                "ROLE_FINANCEIRO", "ROLE_CANDIDATO"
            };

            for (String r : roles) {
                try {
                    stmt.execute("INSERT INTO roles (name) VALUES ('" + r + "') ON CONFLICT (name) DO NOTHING;");
                } catch (Exception e) {}
            }
            System.out.println("[OK] Institutional roles ensured.");

            // 4. Verificar Roles finais
            System.out.println("\n--- Final Database Status ---");
            ResultSet rs = stmt.executeQuery("SELECT name FROM roles ORDER BY name;");
            while (rs.next()) {
                System.out.println(" - [Role]: " + rs.getString("name"));
            }

        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR]: " + e.getMessage());
        }
    }
}
