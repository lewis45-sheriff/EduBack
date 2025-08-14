package com.EduePoa.EP.Authentication.JWT;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUser implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        // Validate that user has a password
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new UsernameNotFoundException("User has no password set: " + username);
        }

        // Return the user directly since it implements UserDetails
        return user;

        // Alternative approach if you want to create a new instance:
        // return User.builder()
        //         .id(user.getId())
        //         .email(user.getEmail())
        //         .password(user.getPassword())
        //         .role(user.getRole())
        //         .enabledFlag(user.getEnabledFlag())
        //         .deletedFlag(user.getDeletedFlag())
        //         .is_lockedFlag(user.getIs_lockedFlag())
        //         // ... other fields as needed
        //         .build();
    }
}