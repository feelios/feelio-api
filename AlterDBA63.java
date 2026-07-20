import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class AlterDBA63 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/feelio?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String user = "root";
        String password = "1234";

        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement();
            String sql = "ALTER TABLE goals ADD COLUMN initial_amount BIGINT NOT NULL DEFAULT 0;";
            stmt.executeUpdate(sql);
            System.out.println("success");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
