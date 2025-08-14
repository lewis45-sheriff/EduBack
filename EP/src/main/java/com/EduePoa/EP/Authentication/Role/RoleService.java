package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.Enum.Status;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;


    public void createRole(@NonNull String name) {
        try {
            Role role = new Role();
            role.setName(name);
            role.setEnabledFlag('Y');
            role.setStatus(Status.ACTIVE);
            role = roleRepository.save(role);

            log.info("Role created: {}", role);
        } catch (DataIntegrityViolationException e) {
            log.error("Database constraint error while creating role: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error occurred while creating role: {}", e.getMessage());
        }
    }


}
