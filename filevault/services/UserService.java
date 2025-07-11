package com.FVProject.filevault.services;

import com.FVProject.filevault.DTO.UserLoginRequest;
import com.FVProject.filevault.DTO.UserRegisterRequest;
import com.FVProject.filevault.model.User;
import com.FVProject.filevault.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Value("${spring.cloud.gcp.credentials.location})")
    private String KEY;

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String BUCKET;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Long createUser(UserRegisterRequest request) {
        // Check if username or email is already taken
        if (userRepository.findByUsername(request.getUsername()) != null ||
                userRepository.findByEmail(request.getEmail()) != null) {
            return null; // Return null if user already exists
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setEnabled(true);
        user.setLocked(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        return user.getId(); // Return the user's ID for IAM role assignment
    }


    public boolean authenticate(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null || !user.isEnabled() || user.isLocked()) {
            return false;
        }

        return passwordEncoder.matches(request.getPassword(), user.getPassword());
    }

    // Get user by username
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getAllUsers(String requestingUsername) {
        User requester = userRepository.findByUsername(requestingUsername);

        if (requester == null || !requester.getRole().equalsIgnoreCase("ADMIN")) {
            throw new SecurityException("Access denied: Admins only.");
        }

        return userRepository.findAll();
    }

    public User getUserByUsername(String username) {
        Optional<User> userOpt = Optional.ofNullable(userRepository.findByUsername(username));

        if (userOpt.isPresent()) {
            return userOpt.get();
        } else {
            System.out.println("User not found with username: " + username);
            return null;
        }
    }
    public boolean assignUserStorageRole(String userEmail, Long userId) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "gcloud", "projects", "add-iam-policy-binding", KEY,
                    "--member=user:" + userEmail,
                    "--role=projects/" + KEY + "/roles/userFileManager",
                    "--condition=expression=request.resource.name.startsWith(\"projects/_/buckets/" + BUCKET + "/objects/user_" + userId + "/\"), title=\"User-specific file access\", description=\"Restrict access to user's directory\""
            );
            processBuilder.start();
            return true;
        } catch (IOException e) {
            System.err.println("Failed to assign IAM role: " + e.getMessage());
        }
        return false;
    }
}



