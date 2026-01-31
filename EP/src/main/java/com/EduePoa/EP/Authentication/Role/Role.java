package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Enum.Status;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

@Data
@RequiredArgsConstructor
@Entity
@EqualsAndHashCode(exclude = "rolePermissions")
@ToString(exclude = "rolePermissions")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, name = "name")
    private String name;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_on")
    private Timestamp createdOn;

    @UpdateTimestamp
    @Column(name = "updated_on")
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp updatedOn;

    @Column(name = "enabled_flag")
    private char enabledFlag = 'Y';

    @Column(name = "deleted_flag")
    private char deletedFlag = 'N';

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    // Helper method to add permission
    public void addPermission(Permissions permission) {
        RolePermission rolePermission = new RolePermission(this, permission);
        rolePermissions.add(rolePermission);
    }

    // Helper method to remove permission
    public void removePermission(Permissions permission) {
        rolePermissions.removeIf(rp -> rp.getPermission().equals(permission));
    }
}