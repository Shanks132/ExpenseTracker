package shanks.personal.demo.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import shanks.personal.demo.dto.UpdateUserRequest;
import shanks.personal.demo.entity.User;
import shanks.personal.demo.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<User> getMyDetails(HttpServletRequest req) {
        User user = userService.getUserById((Long) req.getAttribute("userId"));
        System.out.println("user controller"    );
        return ResponseEntity.ok(user);
    }
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String token = request.getHeader("Authorization").substring(7);
        userService.deleteUser(userId, token);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/me")
    public ResponseEntity<User> updateMyDetails(
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        User updated = userService.updateUser(userId, request);
        return ResponseEntity.ok(updated);
    }
}
