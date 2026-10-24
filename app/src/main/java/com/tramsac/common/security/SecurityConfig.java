package com.tramsac.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.common.ApiError;
import com.tramsac.nguoidung.NguoiDungRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bảo mật: JWT không trạng thái, quyền theo vai trò kiểm tra ở phía server (QĐ4).
 * Công khai: /actuator/health và /api/auth/**; mọi đường dẫn khác cần xác thực.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Hệ thống tự xác thực bằng DangNhapService; khai báo bean này để Spring không tạo tài khoản mặc định. */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            NguoiDungRepository nguoiDungRepository,
                                            ObjectMapper objectMapper) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, nguoiDungRepository);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/**", "/api/webhook/thanh-toan").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> ghiLoi(res, objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED, ApiError.of("CHUA_XAC_THUC", "Cần đăng nhập.")))
                        .accessDeniedHandler((req, res, ex) -> ghiLoi(res, objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                ApiError.of("KHONG_DU_QUYEN", "Không đủ quyền thực hiện."))))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void ghiLoi(HttpServletResponse res, ObjectMapper om, int status, ApiError body)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        om.writeValue(res.getOutputStream(), body);
    }
}
