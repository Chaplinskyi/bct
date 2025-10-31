package ua.karpaty.barcodetracker.Config; // Або ваш пакет конфігурації

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher; // Для logout

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Бін для шифрування паролів
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Бін для налаштування користувачів (в пам'яті)
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Створюємо користувача "admin"
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("826499")) // Ваш пароль адміна
                .roles("ADMIN") // Роль ADMIN
                .build();

        // --- ДОДАНО: Створюємо нового користувача "user" ---
        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder.encode("karpaty123")) // Новий пароль
                .roles("USER") // Призначаємо роль USER (можна змінити, якщо потрібно)
                .build();
        // --- КІНЕЦЬ ДОДАНОЇ ЧАСТИНИ ---

        // Менеджер користувачів, що зберігає їх у пам'яті
        // ОНОВЛЕНО: Передаємо обох користувачів
        return new InMemoryUserDetailsManager(admin, user);
    }

    // Бін для налаштування правил доступу та форми логіну
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                // Дозволяємо доступ до статичних ресурсів
                                .requestMatchers("/css/**", "/images/**", "/webjars/**").permitAll()
                                // !!! ДОДАЙ ЦЕЙ РЯДОК: Дозволяємо доступ до сторінки логіну !!!
                                .requestMatchers("/login").permitAll()
                                // Всі інші запити вимагають автентифікації
                                .anyRequest().authenticated()
                )
                .formLogin(formLogin -> // Ця секція залишається без змін
                        formLogin
                                .loginPage("/login")
                                .loginProcessingUrl("/perform_login")
                                .defaultSuccessUrl("/dashboard", true)
                                .failureUrl("/login?error=true")
                                .permitAll()
                )
                .logout(logout -> // Ця секція залишається без змін
                        logout
                                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                .logoutSuccessUrl("/login?logout=true")
                                .deleteCookies("JSESSIONID")
                                .invalidateHttpSession(true)
                                .permitAll()
                );

        return http.build();
    }
}