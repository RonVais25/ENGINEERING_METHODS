package server.db;

public class testconn {
    public static void main(String[] args) {
        try {
            DBConnection.getConnection();
            System.out.println("Connected to GoNature DB successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}