package shanks.personal.demo.service;

import org.springframework.transaction.annotation.Transactional;
import shanks.personal.demo.dto.UpdateUserRequest;
import shanks.personal.demo.entity.User;
import shanks.personal.demo.exceptions.NotFoundException;
import shanks.personal.demo.repository.TransactionRepository;
import shanks.personal.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import shanks.personal.demo.security.JwtUtil;
import shanks.personal.demo.security.TokenBlacklistService;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public User registerUser(User user) {
        // Hash the password before saving -- never store plain text
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + id));
    }

    public User getUserByUsername(String username) {
        System.out.println(username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found with username: " + username));
    }

    public boolean checkPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
    @Transactional
    public void deleteUser(Long userId, String token) { 
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        transactionRepository.deleteByUserId(userId);
        userRepository.delete(user);
        long remainingExpiry = jwtUtil.getRemainingExpiry(token);
        tokenBlacklistService.blacklist(token, remainingExpiry);
    }

    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            // hash the new password before saving, same as registration
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return userRepository.save(user);
    }
}
