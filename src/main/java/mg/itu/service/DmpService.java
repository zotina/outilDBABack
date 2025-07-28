package mg.itu.service;

import mg.itu.model.OracleUser;
import mg.itu.repository.OracleUserRepository;
import mg.itu.util.OracleDockerManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DmpService {
    
    @Autowired
    private OracleUserRepository userRepository;
    
    @Value("${oracle.container.name:oracle-11g-xe}")
    private String containerName;
    
    @Value("${oracle.sid:ORCL}")
    private String oracleSid;
    
    @Value("${oracle.host:192.168.88.240}")
    private String oracleHost;
    
    @Value("${oracle.port:1521}")
    private String oraclePort;
    
    @Value("${dmp.export.directory:/tmp/exports}")
    private String exportDirectory;
    
    @Value("${dmp.import.directory:/tmp/imports}")
    private String importDirectory;
    
    @Value("${process.timeout.minutes:30}")
    private long processTimeoutMinutes;
    
    private volatile String cachedExpCommand = null;
    private volatile String cachedImpCommand = null;

    public List<String> getAllUsernames() {
        try {
            List<OracleUser> users = userRepository.findActiveNonSystemUsers();
            List<String> usernames = new ArrayList<>();
            for (OracleUser user : users) {
                if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
                    usernames.add(user.getUsername());
                }
            }
            return usernames;
        } catch (Exception e) {
            List<OracleUser> users = userRepository.findAll();
            List<String> usernames = new ArrayList<>();
            for (OracleUser user : users) {
                if (user.getUsername() != null && !user.getUsername().trim().isEmpty() 
                    && !"SYS".equals(user.getUsername()) && !"SYSTEM".equals(user.getUsername())) {
                    usernames.add(user.getUsername());
                }
            }
            return usernames;
        }
    }
    
    private boolean validateUser(String username) {
        try {
            return userRepository.existsActiveUser(username);
        } catch (Exception e) {
            return true;
        }
    }

    public String generateAllocateExtentScriptOptimized(String username) throws IOException, InterruptedException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide");
        }

        String query = String.format(
            "SELECT  'ALTER TABLE ' || TABLE_NAME || ' ALLOCATE EXTENT;' " +
            "FROM DBA_TABLES " +
            "WHERE OWNER='%s' AND (NUM_ROWS IS NULL OR NUM_ROWS = 0) " +
            "ORDER BY TABLE_NAME;", 
            username.toUpperCase()
        );

        String[] command = {
            "docker", "exec", containerName,
            "sqlplus", "-S", "sys/oracle@" + oracleSid, "as", "sysdba"
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("SET PAGESIZE 0");
            writer.println("SET FEEDBACK OFF");
            writer.println("SET HEADING OFF");
            writer.println("SET TIMING OFF");
            writer.println("SET LINESIZE 200");
            writer.println(query);
            writer.println("EXIT;");
            writer.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("ALTER TABLE")) {
                    output.append(line.trim()).append("\n");
                }
            }
        }

        boolean finished = process.waitFor(1, TimeUnit.MINUTES); 
        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("Timeout lors de la génération du script ALLOCATE EXTENT");
        }
        
        return output.toString();
    }

    public String executeAllocateExtentScriptOptimized(String username, String allocateScript) throws IOException, InterruptedException {
        if (allocateScript == null || allocateScript.trim().isEmpty()) {
            return "Aucun script à exécuter";
        }

        String[] command = {
            "docker", "exec", containerName,
            "sqlplus", "-S", "sys/oracle@" + oracleSid, "as", "sysdba"
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("SET TIMING OFF");
            writer.println("SET FEEDBACK OFF"); 
            writer.println("SET ECHO OFF");
            writer.println("BEGIN");
            
            String[] statements = allocateScript.split("\n");
            for (String statement : statements) {
                if (statement.trim().startsWith("ALTER TABLE")) {
                    writer.println("  EXECUTE IMMEDIATE '" + statement.trim().replace("'", "''") + "';");
                }
            }
            
            writer.println("  COMMIT;");
            writer.println("END;");
            writer.println("/");
            writer.println("EXIT;");
            writer.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(3, TimeUnit.MINUTES); 
        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("Timeout lors de l'exécution du script ALLOCATE EXTENT");
        }
        
        return "Scripts exécutés en batch:\n" + output.toString();
    }

    private String findExpCommandCached() throws IOException, InterruptedException {
        if (cachedExpCommand != null) {
            return cachedExpCommand;
        }
        
        synchronized (this) {
            if (cachedExpCommand != null) {
                return cachedExpCommand;
            }
            
            cachedExpCommand = findExpCommand();
            return cachedExpCommand;
        }
    }

    private String findExpCommand() throws IOException, InterruptedException {
        String[] possiblePaths = {
            "/u01/app/oracle/product/11.2.0/xe/bin/exp",
            "/u01/app/oracle/product/bin/exp",
            "$ORACLE_HOME/bin/exp"
        };
        
        for (String path : possiblePaths) {
            String[] command = {"docker", "exec", containerName, "sh", "-c", "ls " + path + " 2>/dev/null || echo ''"};
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                if (result != null && result.trim().endsWith("exp")) {
                    return result.trim();
                }
            }
            process.waitFor(3, TimeUnit.SECONDS); 
        }
        
        return "exp"; 
    }
    
    public byte[] exportDmpOptimized(String dmpFileName, String username, String password) throws IOException, InterruptedException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }
        
        if (!validateUser(username)) {
            throw new IllegalArgumentException("L'utilisateur '" + username + "' n'existe pas ou n'est pas actif");
        }
        
        String allocateScript = "";
        try {
            allocateScript = generateAllocateExtentScriptOptimized(username);
            if (!allocateScript.trim().isEmpty()) {
                executeAllocateExtentScriptOptimized(username, allocateScript);
            }
        } catch (Exception e) {
            System.err.println("Erreur ALLOCATE EXTENT (continuant quand même): " + e.getMessage());
        }
        
        dmpFileName = sanitizeFileName(dmpFileName);
        if (!dmpFileName.endsWith(".dmp")) {
            dmpFileName += ".dmp";
        }
        
        createDirectoryIfNotExists(exportDirectory);
        
        String containerFilePath = "/tmp/" + dmpFileName;
        String logFileName = dmpFileName.replace(".dmp", ".log");
        String containerLogPath = "/tmp/" + logFileName;

        try {
            String expCommand = findExpCommandCached(); 
            String connectString = String.format("%s/%s", username.toLowerCase(), password);
            System.out.println("connection = " + connectString + "@//" + oracleHost + ":" + oraclePort + "/" + oracleSid);
            
            String[] command = {
                "docker", "exec", "-i", containerName, "bash", "-c",
                String.format("su - oracle -c 'export ORACLE_HOME=/u01/app/oracle/product/11.2.0/xe && " +
                              "export PATH=$ORACLE_HOME/bin:$PATH && " +
                              "%s %s@//%s:%s/%s file=%s statistics=none compress=y direct=y recordlength=65535 buffer=10485760'",
                              expCommand, connectString, oracleHost, oraclePort, oracleSid, containerFilePath)
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            ByteArrayOutputStream dmpOutput = new ByteArrayOutputStream();
            StringBuilder logOutput = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(processTimeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new InterruptedException("L'export a dépassé le délai d'attente de " + processTimeoutMinutes + " minutes");
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                String[] catCommand = {
                    "docker", "exec", containerName, "cat", containerFilePath
                };
                ProcessBuilder catPb = new ProcessBuilder(catCommand);
                catPb.redirectErrorStream(true);
                Process catProcess = catPb.start();

                try (InputStream inputStream = catProcess.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        dmpOutput.write(buffer, 0, bytesRead);
                    }
                }

                boolean catFinished = catProcess.waitFor(30, TimeUnit.SECONDS);
                if (!catFinished) {
                    catProcess.destroyForcibly();
                    throw new IOException("Timeout lors de la lecture du fichier DMP depuis le conteneur");
                }

                int catExitCode = catProcess.exitValue();
                if (catExitCode != 0) {
                    throw new IOException("Échec de la lecture du fichier DMP depuis le conteneur (code: " + catExitCode + ")");
                }

                byte[] fileContent = dmpOutput.toByteArray();
                if (fileContent.length == 0) {
                    throw new IOException("Export terminé mais le fichier DMP est vide.");
                }

                cleanupContainerFile(containerName, containerFilePath);
                cleanupContainerFile(containerName, containerLogPath);

                return fileContent;
            } else {
                throw new IOException("Erreur export (Code: " + exitCode + "). Détails : " + logOutput.toString().substring(0, Math.min(500, logOutput.length())));
            }

        } catch (Exception e) {
            throw new IOException("Erreur lors de l'export optimisé : " + e.getMessage(), e);
        }
    }

    private void copyFileFromContainerOptimized(String containerName, String containerPath, String hostPath) throws IOException, InterruptedException {
        String[] command = {"docker", "cp", containerName + ":" + containerPath, hostPath};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        boolean finished = process.waitFor(30, TimeUnit.SECONDS); 
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timeout lors de la copie du fichier depuis le conteneur");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Échec de la copie du fichier depuis le conteneur (code: " + exitCode + ")");
        }
    }
    
    private String findImpCommandCached() throws IOException, InterruptedException {
        if (cachedImpCommand != null) {
            return cachedImpCommand;
        }
        
        synchronized (this) {
            if (cachedImpCommand != null) {
                return cachedImpCommand;
            }
            
            cachedImpCommand = findImpCommand();
            return cachedImpCommand;
        }
    }

    private String findImpCommand() throws IOException, InterruptedException {
        String[] possiblePaths = {
            "/u01/app/oracle/product/11.2.0/xe/bin/imp",
            "/u01/app/oracle/product/bin/imp",
            "$ORACLE_HOME/bin/imp"
        };
        
        for (String path : possiblePaths) {
            String[] command = {"docker", "exec", containerName, "sh", "-c", "ls " + path + " 2>/dev/null || echo ''"};
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                if (result != null && result.trim().endsWith("imp")) {
                    return result.trim();
                }
            }
            process.waitFor(3, TimeUnit.SECONDS);
        }
        
        return "imp";
    }

    public boolean createOracleUser(String username, String password) throws Exception {
        return new OracleDockerManager().createUser(username, password);
    }

    public String importDmpOptimized(MultipartFile dmpFile, String username, String password, boolean createUser) throws IOException, InterruptedException {
        if (dmpFile == null || dmpFile.isEmpty()) {
            throw new IllegalArgumentException("Le fichier DMP ne peut pas être vide");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }

        String originalFileName = dmpFile.getOriginalFilename();
        if (originalFileName == null || !originalFileName.endsWith(".dmp")) {
            throw new IllegalArgumentException("Le fichier doit être un fichier .dmp valide");
        }

        if (createUser) {
            try {
                boolean createResult = createOracleUser(username, password);
                if (!createResult) {
                    throw new IOException("Échec création utilisateur: " + createResult);
                }
            } catch (Exception e) {
                throw new IOException("Erreur création utilisateur '" + username + "': " + e.getMessage(), e);
            }
        } else {
            if (!validateUser(username)) {
                throw new IllegalArgumentException("L'utilisateur '" + username + "' n'existe pas.");
            }
        }

        createDirectoryIfNotExists(importDirectory);

        String sanitizedFileName = sanitizeFileName(originalFileName);
        File tempFile = new File(importDirectory + "/" + "temp_" + System.currentTimeMillis() + "_" + sanitizedFileName);
        String containerFilePath = "/tmp/" + tempFile.getName();
        String logFileName = sanitizedFileName.replace(".dmp", "_import.log");
        String containerLogPath = "/tmp/" + logFileName;
        String hostLogPath = importDirectory + "/" + logFileName;

        try {
            dmpFile.transferTo(tempFile);
            copyFileToContainerOptimized(tempFile.getAbsolutePath(), containerName, containerFilePath);

            String impCommand = findImpCommandCached(); 

            String connectString = String.format("%s/%s", username, password);
            
            String[] command = {
                "docker", "exec", containerName, "sh", "-c",
                String.format("export ORACLE_HOME=/u01/app/oracle/product/11.2.0/xe && " +
                             "export PATH=$ORACLE_HOME/bin:$PATH && " +
                             "%s %s file=%s log=%s full=y ignore=y " +
                             "statistics=none commit=y recordlength=65535 buffer=10485760",
                             impCommand, connectString, containerFilePath, containerLogPath)
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(processTimeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new InterruptedException("L'import a dépassé le délai d'attente");
            }

            int exitCode = process.exitValue();

            try {
                copyFileFromContainerOptimized(containerName, containerLogPath, hostLogPath);
            } catch (Exception e) {
            }

            String userAction = createUser ? " (Utilisateur créé)" : " (Utilisateur existant)";
            
            if (exitCode == 0) {
                return "Import optimisé réussi : " + originalFileName + userAction + ". Journal : " + hostLogPath;
            } else {
                return "Erreur import (Code: " + exitCode + ")" + userAction + ". Journal : " + hostLogPath;
            }

        } catch (Exception e) {
            throw new IOException("Erreur import optimisé : " + e.getMessage(), e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            try {
                cleanupContainerFile(containerName, containerFilePath);
            } catch (Exception e) {
            }
        }
    }

    private void copyFileToContainerOptimized(String hostPath, String containerName, String containerPath) throws IOException, InterruptedException {
        String[] command = {"docker", "cp", hostPath, containerName + ":" + containerPath};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        boolean finished = process.waitFor(60, TimeUnit.SECONDS); 
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timeout lors de la copie vers le conteneur");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Échec copie vers conteneur (code: " + exitCode + ")");
        }
    }

    public byte[] exportDmp(String dmpFileName, String username, String password) throws IOException, InterruptedException {
        return exportDmpOptimized(dmpFileName, username, password);
    }

    public String generateAllocateExtentScript(String username) throws IOException, InterruptedException {
        return generateAllocateExtentScriptOptimized(username);
    }

    public String executeAllocateExtentScript(String username, String allocateScript) throws IOException, InterruptedException {
        return executeAllocateExtentScriptOptimized(username, allocateScript);
    }

    public String importDmp(MultipartFile dmpFile, String username, String password, boolean createUser) throws IOException, InterruptedException {
        return importDmpOptimized(dmpFile, username, password, createUser);
    }

    public String importDmp(MultipartFile dmpFile, String username, String password) throws IOException, InterruptedException {
        return importDmpOptimized(dmpFile, username, password, false);
    }

    public String startOracleContainer() throws IOException, InterruptedException {
        String[] command = {"sudo", "docker", "start", containerName};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("Timeout démarrage conteneur");
        }

        return "Conteneur Oracle: " + output.toString();
    }

    public String diagnoseOracleEnvironment() throws IOException, InterruptedException {
        StringBuilder diagnosis = new StringBuilder();
        
        String[] envCommand = {"docker", "exec", containerName, "sh", "-c", "env | grep ORACLE"};
        ProcessBuilder pb = new ProcessBuilder(envCommand);
        Process process = pb.start();
        
        diagnosis.append("=== Variables d'environnement Oracle ===\n");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                diagnosis.append(line).append("\n");
            }
        }
        process.waitFor(5, TimeUnit.SECONDS); 
        
        return diagnosis.toString();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "export_" + System.currentTimeMillis();
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    private void cleanupContainerFile(String containerName, String containerPath) throws IOException, InterruptedException {
        String[] command = {"docker", "exec", containerName, "rm", "-f", containerPath};
        Process process = new ProcessBuilder(command).start();
        process.waitFor(5, TimeUnit.SECONDS); 
    }
}