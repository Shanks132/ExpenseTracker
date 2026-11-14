package shanks.personal.demo.controller;

import jakarta.validation.Valid;
import shanks.personal.demo.dto.AuthResponse;
import shanks.personal.demo.dto.LoginRequest;
import shanks.personal.demo.dto.RegisterRequest;
import shanks.personal.demo.entity.User;
import shanks.personal.demo.security.JwtUtil;
import shanks.personal.demo.security.TokenBlacklistService;
import shanks.personal.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword()); // gets hashed inside registerUser()

        User created = userService.registerUser(user);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.getUserByUsername(request.getUsername());

        if (!userService.checkPassword(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).build();
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        System.out.println("Token generated");
        return ResponseEntity.ok(new AuthResponse(token));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long remaining = jwtUtil.getRemainingExpiry(token);
            if (remaining > 0) {
                tokenBlacklistService.blacklist(token, remaining);
            }
        }
        return ResponseEntity.noContent().build();
    }
}