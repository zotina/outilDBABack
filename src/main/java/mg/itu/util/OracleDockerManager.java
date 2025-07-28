package mg.itu.util;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class OracleDockerManager {
    
    private static final String CONTAINER_NAME = "oracle-11g-xe";
    private static final String ORACLE_HOME = "/u01/app/oracle/product/11.2.0/xe";
    
    
    public boolean createUser(String username, String password) {
        try {
            String sqlCommands = String.format(
                "CREATE USER %s IDENTIFIED BY %s;\n" +
                "GRANT DBA TO %s;\n" +
                "EXIT;\n", 
                username, password, username
            );
            
            return executeSqlCommand(sqlCommands);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la création de l'utilisateur: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    
    public boolean executeSqlCommand(String sqlCommands) {
        try {
            
            String dockerCommand = String.format(
                "docker exec -i %s bash -c \"su - oracle -c 'export ORACLE_HOME=%s && " +
                "export ORACLE_SID=XE && export PATH=\\$ORACLE_HOME/bin:\\$PATH && sqlplus / as sysdba'\"",
                CONTAINER_NAME, ORACLE_HOME
            );
            
            System.out.println("Exécution de la commande Docker...");
            System.out.println("Commandes SQL à exécuter:");
            System.out.println(sqlCommands);
            
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", dockerCommand);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            
            try (PrintWriter writer = new PrintWriter(process.getOutputStream(), true)) {
                writer.print(sqlCommands);
                writer.flush();
            }
            
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println(line);
                }
            }
            
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("Timeout: La commande a pris trop de temps");
                return false;
            }
            
            int exitCode = process.exitValue();
            System.out.println("Code de sortie: " + exitCode);
            
            
            String outputStr = output.toString();
            return exitCode == 0 && 
                   (outputStr.contains("User created") || outputStr.contains("Grant succeeded") || 
                    outputStr.contains("PL/SQL procedure successfully completed"));
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'exécution de la commande: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    
    public boolean dropUser(String username) {
        String sqlCommands = String.format(
            "DROP USER %s CASCADE;\n" +
            "EXIT;\n", 
            username
        );
        
        return executeSqlCommand(sqlCommands);
    }
    
    
    public boolean userExists(String username) {
        String sqlCommands = String.format(
            "SELECT COUNT(*) FROM dba_users WHERE username = '%s';\n" +
            "EXIT;\n", 
            username.toUpperCase()
        );
        
        return executeSqlCommand(sqlCommands);
    }
    
    
    public boolean testConnection() {
        String sqlCommands = "SELECT 'Connection OK' FROM dual;\nEXIT;\n";
        return executeSqlCommand(sqlCommands);
    }
    
    
    public boolean executeScriptFile(String scriptPath) {
        try {
            StringBuilder sqlContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sqlContent.append(line).append("\n");
                }
            }
            sqlContent.append("EXIT;\n");
            
            return executeSqlCommand(sqlContent.toString());
            
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du fichier: " + e.getMessage());
            return false;
        }
    }
    
    
    public static void main(String[] args) {
        OracleDockerManager manager = new OracleDockerManager();
        
        
        System.out.println("=== Test de connexion ===");
        if (manager.testConnection()) {
            System.out.println("✅ Connexion Oracle réussie");
        } else {
            System.out.println("❌ Échec de la connexion Oracle");
            return;
        }
        
        
        System.out.println("\n=== Création d'utilisateur ===");
        String testUser = "TESTUSER";
        String testPassword = "testpass123";
        
        if (manager.createUser(testUser, testPassword)) {
            System.out.println("✅ Utilisateur créé avec succès");
        } else {
            System.out.println("❌ Échec de la création de l'utilisateur");
        }
        
        
        System.out.println("\n=== Vérification de l'utilisateur ===");
        if (manager.userExists(testUser)) {
            System.out.println("✅ Utilisateur trouvé");
        } else {
            System.out.println("❌ Utilisateur non trouvé");
        }
    }
}