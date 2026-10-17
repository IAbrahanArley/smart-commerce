package com.smartcommerce.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds JSON bytes before parsing, including chunked transfers; rejects cross-origin mutations.
 */
public final class IdentityRequestFilter extends OncePerRequestFilter {
  private final String origin;
  private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

  public IdentityRequestFilter(String origin) {
    this.origin = origin;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/") || SAFE.contains(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    String suppliedOrigin = request.getHeader("Origin");
    if ((suppliedOrigin != null && !origin.equals(suppliedOrigin))
        || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
      response.setStatus(403);
      return;
    }
    if (request.getContentLengthLong() > 8192) {
      response.setStatus(413);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(8193);
    if (body.length > 8192) {
      response.setStatus(413);
      return;
    }
    var input = new ByteArrayInputStream(body);
    var wrapped =
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous JSON only");
              }
            };
          }
        };
    chain.doFilter(wrapped, response);
  }
}
