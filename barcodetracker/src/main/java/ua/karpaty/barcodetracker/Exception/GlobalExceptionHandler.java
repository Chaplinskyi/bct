package ua.karpaty.barcodetracker.Exception; // Або ваш пакет

import org.slf4j.Logger; // Імпорт логера
import org.slf4j.LoggerFactory; // Імпорт логера
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice
public class GlobalExceptionHandler {

    // Додаємо логер
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BarcodeNotFoundException.class)
    public String handleBarcodeNotFound(BarcodeNotFoundException ex, Model model) {
        // Логуємо подію
        log.error("!!! Перехоплено BarcodeNotFoundException: {}", ex.getMessage()); // Використовуємо error рівень для помітності
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    // Додамо тимчасовий обробник для ВСІХ RuntimeException для діагностики
    @ExceptionHandler(RuntimeException.class)
    public String handleGenericRuntimeException(RuntimeException ex, Model model) {
        log.error("!!! Перехоплено ЗАГАЛЬНИЙ RuntimeException: {}", ex.getMessage(), ex); // Логуємо зі стек-трейсом
        model.addAttribute("errorMessage", "Сталася загальна помилка під час виконання: " + ex.getMessage());
        // Можна тимчасово повертати ту ж 404 сторінку або створити окрему
        return "error/404"; // Або "error/general"
    }
}