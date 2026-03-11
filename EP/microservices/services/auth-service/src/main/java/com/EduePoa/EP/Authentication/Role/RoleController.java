package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.Role.Request.RoleEditRequest;
import com.EduePoa.EP.Authentication.Role.Request.RoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/v1/role/")
@RestController
@RequiredArgsConstructor
public class RoleController {
    private final RoleRepository roleRepository;
    private final RoleService roleService;

    @PostMapping("create-role")
    ResponseEntity<?> create(@RequestBody RoleRequest  roleRequest){
        var response =  roleService.newRole(roleRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-roles")
    ResponseEntity<?>getAllRoles(){
        var response = roleService.getAllRoles();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-permissions")
    ResponseEntity<?>getAllPermissions(){
        var response = roleService.getAllPermissions();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PutMapping("edit-role")
    ResponseEntity<?>editRole(@RequestBody RoleEditRequest roleRequest){
        var response = roleService.editRole(roleRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
