package mg.itu.controller;

import mg.itu.model.ExcelMappingConfig;
import mg.itu.model.ExcelMappingConfig.MappingSection;
import mg.itu.service.ExcelProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sql-generator")
public class SqlGeneratorController {
    
    @Autowired
    private ExcelProcessorService excelProcessorService;
    
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> generateSqlScript(
            @RequestParam("excelFile") MultipartFile file,
            @RequestParam("jsonConfig") String jsonConfig) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Le fichier est requis");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (jsonConfig == null || jsonConfig.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "La configuration JSON est requise");
                return ResponseEntity.badRequest().body(response);
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls") 
                    && !fileName.endsWith(".csv") && !fileName.endsWith(".ods"))) {
                response.put("success", false);
                response.put("message", "Veuillez sélectionner un fichier valide (.xlsx, .xls, .csv, .ods)");
                return ResponseEntity.badRequest().body(response);
            }
            
            String sqlScript = excelProcessorService.generateSqlScript(file, jsonConfig);
            
            response.put("success", true);
            response.put("message", "Script SQL généré avec succès");
            response.put("sqlScript", sqlScript);
            response.put("fileName", fileName);
            response.put("fileSize", file.getSize());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Erreur lors de la lecture du fichier: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Erreur lors de la génération du script: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping(value = "/validate-config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> validateJsonConfig(@RequestBody String jsonConfig) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ExcelMappingConfig config = excelProcessorService.parseJsonConfig(jsonConfig);
            
            if (config.getNomTable() == null || config.getNomTable().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Le nom de la table (nomTable) est requis");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (config.getMap() == null || config.getMap().isEmpty()) {
                response.put("success", false);
                response.put("message", "Au moins une section de mapping est requise");
                return ResponseEntity.badRequest().body(response);
            }
            
            for (int i = 0; i < config.getMap().size(); i++) {
                MappingSection section = config.getMap().get(i);
                
                if (section.getDebutdata() == null || section.getDebutdata() < 1) {
                    response.put("success", false);
                    response.put("message", "debutdata doit être spécifié et supérieur à 0 pour la section " + (i + 1));
                    return ResponseEntity.badRequest().body(response);
                }
                
                if (section.getColumnMapping() == null || section.getColumnMapping().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "columnMapping est requis pour la section " + (i + 1));
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            response.put("success", true);
            response.put("message", "Configuration JSON valide");
            response.put("tableName", config.getNomTable());
            response.put("sectionsCount", config.getMap().size());
            
            if (config.getTypeMap() != null) {
                response.put("typeMapFields", config.getTypeMap().size());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Configuration JSON invalide: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping(value = "/excel-info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> getExcelInfo(@RequestParam("excelFile") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Le fichier est requis");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> fileInfo = excelProcessorService.getExcelFileInfo(file);
            
            response.put("success", true);
            response.put("message", "Informations du fichier récupérées avec succès");
            response.putAll(fileInfo);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Erreur lors de l'analyse du fichier: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Service SQL Generator opérationnel");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}