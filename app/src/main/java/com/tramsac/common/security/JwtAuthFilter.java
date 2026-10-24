package com.tramsac.common.security;

import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.NguoiDungRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc "Authorization: Bearer ...", nạp người dùng từ CSDL Ở MỌI YÊU CẦU (vai trò lấy từ CSDL, không tin
 * vai trò trong token) và trả header X-New-Token để phiên trượt theo hoạt động.
 * Không khai báo là @Component để không bị đăng ký thêm vào chuỗi filter của servlet lần nữa.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_TOKEN_MOI = "X-New-Token";
    private static final String TIEN_TO = "Bearer ";

    private final JwtService jwtService;
    private final NguoiDungRepository nguoiDungRepository;

    public JwtAuthFilter(JwtService jwtService, NguoiDungRepository nguoiDungRepository) {
        this.jwtService = jwtService;
        this.nguoiDungRepository = nguoiDungRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(TIEN_TO)) {
            Optional<NguoiDung> nguoiDung = jwtService.docMaND(header.substring(TIEN_TO.length()))
                    .flatMap(nguoiDungRepository::findById);
            nguoiDung.ifPresent(nd -> {
                var quyen = List.of(new SimpleGrantedAuthority("ROLE_" + nd.layVaiTro().name()));
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(nd.getMaND(), null, quyen));
                response.setHeader(HEADER_TOKEN_MOI, jwtService.taoToken(nd.getMaND()));
            });
        }
        chain.doFilter(request, response);
    }
}
