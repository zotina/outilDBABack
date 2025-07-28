package mg.itu.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxSizeException(MaxUploadSizeExceededException exc, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Le fichier est trop volumineux. Taille maximale autorisée : 500MB");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        redirectAttributes.addFlashAttribute("error", "Le fichier est trop volumineux. Taille maximale autorisée : 500MB");
        return "redirect:/";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArguments(IllegalArgumentException exc, HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) {
        if (isApiRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Paramètre invalide : " + exc.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        redirectAttributes.addFlashAttribute("error", "Paramètre invalide : " + exc.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(IOException.class)
    public Object handleIOException(IOException exc, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erreur d'entrée/sortie : " + exc.getMessage());
            System.err.println("IOException in API request: " + exc.getMessage());
            exc.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        redirectAttributes.addFlashAttribute("error", "Erreur d'entrée/sortie : " + exc.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(InterruptedException.class)
    public Object handleInterruptedException(InterruptedException exc, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Opération interrompue : " + exc.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        redirectAttributes.addFlashAttribute("error", "Opération interrompue : " + exc.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception exc, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (isApiRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erreur inattendue : " + exc.getMessage());
            System.err.println("Unexpected exception in API request: " + exc.getMessage());
            exc.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        redirectAttributes.addFlashAttribute("error", "Erreur inattendue : " + exc.getMessage());
        return "redirect:/";
    }
}