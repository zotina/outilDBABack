// package mg.itu.controller;

// import mg.itu.service.DmpService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.core.io.FileSystemResource;
// import org.springframework.core.io.Resource;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.multipart.MultipartFile;
// import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// import java.io.File;

// @Controller
// public class DmpController {
    
//     @Autowired
//     private DmpService dmpService;
    
//     @Value("${dmp.export.directory:/tmp/exports}")
//     private String exportDirectory;

//     @GetMapping("/importL")
//     public String imp(Model model) {
//         try {
//             model.addAttribute("users", dmpService.getAllUsernames());
//         } catch (Exception e) {
//             model.addAttribute("error", "Erreur lors du chargement des utilisateurs : " + e.getMessage());
//         }
//         return "dmp/import";
//     }

//     @GetMapping("/exportL")
//     public String exp(Model model) {
//         try {
//             model.addAttribute("users", dmpService.getAllUsernames());
//         } catch (Exception e) {
//             model.addAttribute("error", "Erreur lors du chargement des utilisateurs : " + e.getMessage());
//         }
//         return "dmp/export";
//     }


//     @PostMapping("/export")
//     public ResponseEntity<Resource> exportDmp(@RequestParam("dmpFileName") String dmpFileName,
//                                             @RequestParam("username") String username,
//                                             @RequestParam("password") String password) {
//         try {
//             String result = dmpService.exportDmp(dmpFileName, username, password);
            
//             if (result.contains("Export réussi")) {
//                 String filePath = extractFilePathFromResult(result);
//                 File file = new File(filePath);
                
//                 if (file.exists() && file.canRead()) {
//                     Resource resource = new FileSystemResource(file);
//                     String downloadFileName = file.getName();
                    
//                     return ResponseEntity.ok()
//                             .header(HttpHeaders.CONTENT_DISPOSITION, 
//                                    "attachment; filename=\"" + downloadFileName + "\"")
//                             .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
//                             .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
//                             .body(resource);
//                 } else {
//                     return ResponseEntity.notFound().build();
//                 }
//             } else {
//                 return ResponseEntity.badRequest()
//                         .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
//                         .body(new org.springframework.core.io.ByteArrayResource(
//                             ("Erreur lors de l'export: " + result).getBytes("UTF-8")));
//             }
            
//         } catch (IllegalArgumentException e) {
//             return ResponseEntity.badRequest()
//                     .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
//                     .body(new org.springframework.core.io.ByteArrayResource(
//                         ("Paramètre invalide: " + e.getMessage()).getBytes()));
//         } catch (Exception e) {
//             return ResponseEntity.internalServerError()
//                     .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
//                     .body(new org.springframework.core.io.ByteArrayResource(
//                         ("Erreur lors de l'export: " + e.getMessage()).getBytes()));
//         }
//     }

//     @PostMapping("/import")
//     public String importDmp(@RequestParam("dmpFile") MultipartFile dmpFile,
//                            @RequestParam("username") String username,
//                            @RequestParam("password") String password,
//                            @RequestParam(value = "createUser", defaultValue = "false") boolean createUser,
//                            RedirectAttributes redirectAttributes) {
//         try {
//             if (dmpFile.isEmpty()) {
//                 redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner un fichier à importer");
//                 return "redirect:/importL";
//             }

            
//             String result = dmpService.importDmp(dmpFile, username, password, createUser);
            
//             if (result.contains("Import réussi")) {
//                 redirectAttributes.addFlashAttribute("message", result);
//             } else {
//                 redirectAttributes.addFlashAttribute("error", result);
//             }
//         } catch (IllegalArgumentException e) {
//             redirectAttributes.addFlashAttribute("error", "Paramètre invalide : " + e.getMessage());
//         } catch (Exception e) {
//             redirectAttributes.addFlashAttribute("error", "Erreur lors de l'import : " + e.getMessage());
//         }
//         return "redirect:/importL";
//     }

//     private String extractFilePathFromResult(String result) {
//         if (result.contains("Export réussi : ")) {
//             String pathPart = result.substring(result.indexOf("Export réussi : ") + "Export réussi : ".length());
//             int endIndex = pathPart.indexOf(" (Taille:");
//             if (endIndex > 0) {
//                 return pathPart.substring(0, endIndex).trim();
//             }
//         }
//         return null;
//     }

//     @PostMapping("/export-message")
//     public String exportDmpWithMessage(@RequestParam("dmpFileName") String dmpFileName,
//                                       @RequestParam("username") String username,
//                                       @RequestParam("password") String password,
//                                       RedirectAttributes redirectAttributes) {
//         try {
//             String result = dmpService.exportDmp(dmpFileName, username, password);
//             if (result.contains("Export réussi")) {
//                 redirectAttributes.addFlashAttribute("message", result);
//             } else {
//                 redirectAttributes.addFlashAttribute("error", result);
//             }
//         } catch (IllegalArgumentException e) {
//             redirectAttributes.addFlashAttribute("error", "Paramètre invalide : " + e.getMessage());
//         } catch (Exception e) {
//             redirectAttributes.addFlashAttribute("error", "Erreur lors de l'export : " + e.getMessage());
//         }
//         return "redirect:/exportL";
//     }  
// }