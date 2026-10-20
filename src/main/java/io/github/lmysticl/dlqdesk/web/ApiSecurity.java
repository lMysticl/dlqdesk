package io.github.lmysticl.dlqdesk.web;

import io.github.lmysticl.dlqdesk.config.DeskProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiSecurity implements WebMvcConfigurer {
  private final byte[] expected;

  public ApiSecurity(DeskProperties settings) {
    expected = ("Bearer " + settings.apiToken()).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler)
              throws Exception {
            if (!(handler instanceof HandlerMethod method)
                || !method.getBeanType().isAnnotationPresent(DeskEndpoint.class)) return true;
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            String supplied = request.getHeader("Authorization");
            if (supplied != null
                && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8)))
              return true;
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/problem+json");
            response
                .getWriter()
                .write(
                    "{\"status\":401,\"title\":\"Authentication required\",\"detail\":\"Supply a valid bearer token.\"}");
            return false;
          }
        });
  }
}
