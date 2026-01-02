package com.EduePoa.EP.Authentication.User;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(unique = true)
    private String username;
    private String firstName;
    private String lastName;

    @Column(unique = true)
    private String email;
    private String password;
    private String token;
    private String passwordResetToken;
    private long passwordResetTokenExpiryTime;
    private String phoneNumber;
    private String idNumber;
    private String gender;
    private Boolean passwordReset;

    @Column(nullable = true)
    private String location;
    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;


    @CreationTimestamp
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp createdOn;

    @UpdateTimestamp
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp updatedOn;

    private char enabledFlag = 'Y';
    private char deletedFlag = 'N';
    private char is_lockedFlag = 'N';
    private boolean forcePasswordReset = true;

    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Constructor for CustomUser service
    public User(Long id, Collection<? extends GrantedAuthority> authorities, String password) {
        this.id = id;
        this.password = password;
        // You might want to set other fields as needed
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return actual authorities based on role
        if (role != null) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        }
        return List.of();
    }

    @Override
    public String getPassword() {
        // Return the actual password field, not empty string
        return this.password;
    }

    @Override
    public String getUsername() {
        // Return email as username (since you're using email for login)
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Or implement your logic
    }

    @Override
    public boolean isAccountNonLocked() {
        return is_lockedFlag == 'N';
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Or implement your logic
    }

    @Override
    public boolean isEnabled() {
        return enabledFlag == 'Y' && deletedFlag == 'N';
    }
}