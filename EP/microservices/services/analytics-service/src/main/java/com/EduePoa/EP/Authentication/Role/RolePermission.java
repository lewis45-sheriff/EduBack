package com.EduePoa.EP.Authentication.Role;
import com.EduePoa.EP.Authentication.Enum.Permissions;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@Entity
@Table(name = "role_permissions")
@EqualsAndHashCode(exclude = "role")
@ToString(exclude = "role")
public class RolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Permissions permission;

    public RolePermission(Role role, Permissions permission) {
        this.role = role;
        this.permission = permission;
    }
}