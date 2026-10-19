package com.chatapp.service;

import com.chatapp.dto.RegisterRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.dto.UserSummary;
import com.chatapp.entity.User;
import com.chatapp.exception.DuplicateResourceException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PresenceService presenceService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       PresenceService presenceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.presenceService = presenceService;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getCreatedAt());
    }

    public UserResponse getByUsername(String username) {
        User user = findUser(username);
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    /** Finds other users whose name contains the search text. Empty text returns the first 20 users. */
    public List<UserSummary> search(String currentUsername, String query) {
        User current = findUser(currentUsername);
        String text = query == null ? "" : query.trim();

        return userRepository
                .findTop20ByUsernameContainingIgnoreCaseAndIdNotOrderByUsernameAsc(text, current.getId())
                .stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(),
                        presenceService.isOnline(u.getUsername()), u.getLastSeen()))
                .toList();
    }

    /** Records "last seen" when a user's last session closes. Returns the stored time. */
    @Transactional
    public LocalDateTime touchLastSeen(String username) {
        LocalDateTime now = LocalDateTime.now();
        userRepository.findByUsername(username).ifPresent(user -> user.setLastSeen(now));
        return now;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
