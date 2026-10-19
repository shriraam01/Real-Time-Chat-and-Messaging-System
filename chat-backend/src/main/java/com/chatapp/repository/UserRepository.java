package com.chatapp.repository;

import com.chatapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByUsername(String username);

    // user search for starting chats and adding group members (never returns the caller)
    List<User> findTop20ByUsernameContainingIgnoreCaseAndIdNotOrderByUsernameAsc(String username, Long id);
}
