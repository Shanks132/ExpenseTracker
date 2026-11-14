package shanks.personal.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import shanks.personal.demo.dto.UpdateUserRequest;
import shanks.personal.demo.entity.User;
import shanks.personal.demo.exceptions.NotFoundException;
import shanks.personal.demo.repository.TransactionRepository;
import shanks.personal.demo.repository.UserRepository;
import shanks.personal.demo.security.JwtUtil;
import shanks.personal.demo.security.TokenBlacklistService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks private UserService userService;

    private static User user(Long id, String username, String email, String passwordHash) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordHash);
        return u;
    }

    @Test
    void registerUserStoresAHashedPasswordNotThePlainOne() {
        User newUser = user(null, "alice", "alice@example.com", "plain-pw");
        when(passwordEncoder.encode("plain-pw")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerUser(newUser);

        assertThat(saved.getPassword()).isEqualTo("hashed-pw");
        verify(userRepository).save(newUser);
    }

    @Test
    void checkPasswordDelegatesToTheEncoder() {
        when(passwordEncoder.matches("raw", "hash")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThat(userService.checkPassword("raw", "hash")).isTrue();
        assertThat(userService.checkPassword("wrong", "hash")).isFalse();
    }

    @Test
    void getUserByIdReturnsTheUser() {
        User existing = user(1L, "alice", "alice@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(userService.getUserById(1L)).isSameAs(existing);
    }

    @Test
    void getUserByIdThrowsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUserByUsernameThrowsWhenMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("ghost"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateUserChangesOnlyTheProvidedFields() {
        User existing = user(1L, "alice", "alice@example.com", "old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("alice2");

        User updated = userService.updateUser(1L, request);

        assertThat(updated.getUsername()).isEqualTo("alice2");
        assertThat(updated.getEmail()).isEqualTo("alice@example.com");
        assertThat(updated.getPassword()).isEqualTo("old-hash");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateUserRehashesANewPassword() {
        User existing = user(1L, "alice", "alice@example.com", "old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-pw")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("new-pw");

        User updated = userService.updateUser(1L, request);

        assertThat(updated.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void deleteUserRemovesTransactionsThenTheUserThenBlacklistsTheToken() {
        User existing = user(1L, "alice", "alice@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(jwtUtil.getRemainingExpiry("tok")).thenReturn(5_000L);

        userService.deleteUser(1L, "tok");

        InOrder order = inOrder(transactionRepository, userRepository, tokenBlacklistService);
        order.verify(transactionRepository).deleteByUserId(1L);
        order.verify(userRepository).delete(existing);
        order.verify(tokenBlacklistService).blacklist("tok", 5_000L);
    }

    @Test
    void deleteUserThrowsAndTouchesNothingWhenTheUserIsMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L, "tok"))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(transactionRepository, tokenBlacklistService);
    }
}
