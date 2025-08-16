package com.EduePoa.EP.Authentication.Config;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.Role.RoleService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Utils.ResourceNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class CreateAdmin implements ApplicationRunner {

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        addAdminRole();
        addAdmin();
        addParentsRole();
    }

    void addAdminRole() {
        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
             log.info("Creating ROLE_ADMIN role on " + LocalDateTime.now());
            roleService.createRole("ROLE_ADMIN");
        } else {
            log.info("ROLE_ADMIN role already exists.");
        }
    }

    void addParentsRole() {
        if (roleRepository.findByName("ROLE_PARENT").isEmpty()) {
            log.info("Creating ROLE_PARENT role on " + LocalDateTime.now());
            roleService.createRole("ROLE_PARENT");
        } else {
            log.info("ROLE_PARENT role already exists.");
        }
    }

    void addAdmin() {
        try {
            Integer adminCount = userRepository.adminCount("ROLE_ADMIN");

            if (adminCount > 0) {
                log.info("System admin already exists.");
            } else {
                Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                        .orElseThrow(() -> new ResourceNotFoundException("Role with name ROLE_ADMIN not found"));

                User user = new User();
                user.setUsername("Admin");
                user.setEmail("admin1.system@info");
                user.setFirstName("Super");
                user.setLastName("Admin");
                user.setPassword(passwordEncoder.encode("1234"));
                user.setEnabledFlag('Y');
                user.setStatus(Status.ACTIVE);
                user.setRole(adminRole);
                user.setLocation("EM-TECH");
                user.setGender("null");
                user.setDeletedFlag('N');
                userRepository.save(user);

                log.info("System admin created successfully.");
            }
        } catch (Exception e) {
            log.error("Error while creating admin: " + e.getMessage(), e);
        }
    }
}
