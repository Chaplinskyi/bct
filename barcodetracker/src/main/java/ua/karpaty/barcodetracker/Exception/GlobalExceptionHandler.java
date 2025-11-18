package ua.karpaty.barcodetracker.Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BarcodeNotFoundException.class)
    public String handleBarcodeNotFound(BarcodeNotFoundException ex, Model model) {
        log.error("!!! Перехоплено BarcodeNotFoundException: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(RuntimeException.class)
    public String handleGenericRuntimeException(RuntimeException ex, Model model) {
        log.error("!!! Перехоплено ЗАГАЛЬНИЙ RuntimeException: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Сталася загальна помилка під час виконання: " + ex.getMessage());
        return "error/404";
    }
}