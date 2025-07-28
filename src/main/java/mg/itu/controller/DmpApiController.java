package mg.itu.controller;

import mg.itu.service.DmpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DmpApiController {
    
    @Autowired
    private DmpService dmpService;
    
    @Value("${dmp.export.directory:/tmp/exports}")
    private String exportDirectory;

    @GetMapping("/importL")
    public ResponseEntity<Map<String, Object>> getImportData() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("users", dmpService.getAllUsernames());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Erreur lors du chargement des utilisateurs : " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/exportL")
    public ResponseEntity<Map<String, Object>> getExportData() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("users", dmpService.getAllUsernames());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Erreur lors du chargement des utilisateurs : " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/export")
    public ResponseEntity<Resource> exportDmp(@RequestParam("dmpFileName") String dmpFileName,
                                             @RequestParam("username") String username,
                                             @RequestParam("password") String password) {
        try {
            byte[] fileContent = dmpService.exportDmpOptimized(dmpFileName, username, password);
            
            
            String downloadFileName = dmpFileName.endsWith(".dmp") ? dmpFileName : dmpFileName + ".dmp";
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + downloadFileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileContent.length))
                    .body(resource);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                    .body(new ByteArrayResource(
                        ("Paramètre invalide: " + e.getMessage()).getBytes()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                    .body(new ByteArrayResource(
                        ("Erreur lors de l'export: " + e.getMessage()).getBytes()));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> importDmp(@RequestParam("dmpFile") MultipartFile dmpFile,
                                                        @RequestParam("username") String username,
                                                        @RequestParam("password") String password,
                                                        @RequestParam(value = "createUser", defaultValue = "false") boolean createUser) {
        Map<String, String> response = new HashMap<>();
        try {
            if (dmpFile.isEmpty()) {
                response.put("error", "Veuillez sélectionner un fichier à importer");
                return ResponseEntity.badRequest().body(response);
            }

            String result = dmpService.importDmp(dmpFile, username, password, createUser);
            
            if (result.contains("Import optimisé réussi")) {
                response.put("success", result);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("error", "Paramètre invalide : " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("error", "Erreur lors de l'import : " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/export-message")
    public ResponseEntity<Map<String, String>> exportDmpWithMessage(@RequestParam("dmpFileName") String dmpFileName,
                                                                  @RequestParam("username") String username,
                                                                  @RequestParam("password") String password) {
        Map<String, String> response = new HashMap<>();
        try {
            byte[] fileContent = dmpService.exportDmpOptimized(dmpFileName, username, password);
            response.put("success", "Export optimisé réussi : " + dmpFileName + " (Taille: " + fileContent.length + " bytes)");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("error", "Paramètre invalide : " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("error", "Erreur lors de l'export : " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}