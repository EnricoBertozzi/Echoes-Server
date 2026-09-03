package com.n0hana.echoes_server.config;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.n0hana.echoes_server.model.Token;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.service.auth.JwtTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Order(1)
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final UserRepository userRepository;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        var token = this.recoverToken(request);
        if (token != null) {
            try {
                String jti = tokenService.extractJti(token); // novo método
                Optional<Token> opt = tokenService.findByJti(jti);
                if (opt.isPresent() && !opt.get().isRevoked()) {
                    var uuid = tokenService.validadeToken(token); // mantém validação do JWT
                    var userExists = userRepository.findById(UUID.fromString(uuid));
                    if (userExists.isEmpty()) {
                        filterChain.doFilter(request, response);
                        return;
                    }
        
                    UserDetails user = userExists.get();
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities()
                    );
        
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JWTVerificationException e) {

            }
            
        }

      filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
      var cookies = request.getCookies();

      if (cookies != null) {
          for (Cookie cookie : cookies) {
              if ("access_token".equals(cookie.getName())) {
                  return cookie.getValue();
              }
          }
      }

      var authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
          return authHeader.substring(7);
      }

      return null;
    }    
}
