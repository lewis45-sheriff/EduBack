package com.EduePoa.EP.OrgUnit.Service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitCreateRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitReparentRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitResponse;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitUpdateRequest;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import com.EduePoa.EP.OrgUnit.Repository.OrgUnitRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transactional management of the organizational hierarchy with full tree-integrity enforcement
 * (design §5.2): single active root, no self-parenting, no move-beneath-descendant, atomic lineage
 * maintenance, and controlled deactivation that preserves historical budget references.
 *
 * <p>The materialized {@link OrgUnit#getPath()} is an id path like {@code "/1/4/9/"}. A unit's
 * subtree is every unit whose path starts with that unit's path, so cycle checks and roll-ups are
 * prefix tests. Re-parenting recomputes the moved unit's path and rewrites the path prefix of every
 * descendant in one transaction.
 */
@Service
@RequiredArgsConstructor
public class OrgUnitServiceImpl implements OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final UserRepository userRepository;
    private final BudgetLineRepository budgetLineRepository;

    // ------------------------------------------------------------------ CREATE

    @Override
    @Audit(module = "ORG_UNIT", action = "CREATE")
    @Transactional
    public CustomResponse<?> create(OrgUnitCreateRequest request) {
        try {
            String code = normalizeCode(request.getCode());
            if (code.isEmpty()) {
                return status(HttpStatus.BAD_REQUEST, "code is required");
            }
            if (orgUnitRepository.existsByCode(code)) {
                return status(HttpStatus.CONFLICT, "An organizational unit with code '" + code + "' already exists");
            }

            OrgUnit parent = null;
            if (request.getParentId() != null) {
                parent = orgUnitRepository.findById(request.getParentId()).orElse(null);
                if (parent == null) {
                    return status(HttpStatus.BAD_REQUEST, "Parent unit not found: " + request.getParentId());
                }
                if (parent.getStatus() != OrgUnitStatus.ACTIVE) {
                    return status(HttpStatus.BAD_REQUEST, "Cannot attach a unit under an inactive parent");
                }
            } else {
                // Root creation: enforce exactly one active root per tenant.
                if (!orgUnitRepository.findRoots(OrgUnitStatus.ACTIVE).isEmpty()) {
                    return status(HttpStatus.CONFLICT,
                            "A root organizational unit already exists; non-root units must specify a parent");
                }
            }

            User headUser = resolveHeadUser(request.getHeadUserId());
            User currentUser = getCurrentUser();

            OrgUnit unit = OrgUnit.builder()
                    .name(request.getName().trim())
                    .code(code)
                    .type(request.getType().trim().toUpperCase())
                    .parent(parent)
                    .status(OrgUnitStatus.ACTIVE)
                    .headUser(headUser)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .depth(parent == null ? 0 : parent.getDepth() + 1)
                    .path("/") // temporary; finalized after we have an id
                    .build();
            OrgUnit saved = orgUnitRepository.save(unit);
            // Now that we have the id, set the materialized path.
            saved.setPath(parent == null ? "/" + saved.getId() + "/" : parent.getPath() + saved.getId() + "/");
            saved = orgUnitRepository.save(saved);

            return ok(HttpStatus.CREATED, "Organizational unit created successfully", toResponse(saved, false));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ------------------------------------------------------------------ UPDATE

    @Override
    @Audit(module = "ORG_UNIT", action = "UPDATE")
    @Transactional
    public CustomResponse<?> update(Long id, OrgUnitUpdateRequest request) {
        try {
            OrgUnit unit = orgUnitRepository.findById(id).orElse(null);
            if (unit == null) {
                return status(HttpStatus.NOT_FOUND, "Organizational unit not found: " + id);
            }
            unit.setName(request.getName().trim());
            unit.setType(request.getType().trim().toUpperCase());
            unit.setHeadUser(resolveHeadUser(request.getHeadUserId()));
            unit.setUpdatedAt(LocalDateTime.now());
            OrgUnit saved = orgUnitRepository.save(unit);
            return ok(HttpStatus.OK, "Organizational unit updated successfully", toResponse(saved, false));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ---------------------------------------------------------------- REPARENT

    @Override
    @Audit(module = "ORG_UNIT", action = "UPDATE")
    @Transactional
    public CustomResponse<?> reparent(Long id, OrgUnitReparentRequest request) {
        try {
            OrgUnit unit = orgUnitRepository.findById(id).orElse(null);
            if (unit == null) {
                return status(HttpStatus.NOT_FOUND, "Organizational unit not found: " + id);
            }
            if (unit.getParent() == null) {
                return status(HttpStatus.BAD_REQUEST, "The root unit cannot be re-parented");
            }
            Long newParentId = request.getNewParentId();
            if (newParentId.equals(id)) {
                return status(HttpStatus.BAD_REQUEST, "A unit cannot be its own parent");
            }
            OrgUnit newParent = orgUnitRepository.findById(newParentId).orElse(null);
            if (newParent == null) {
                return status(HttpStatus.BAD_REQUEST, "New parent unit not found: " + newParentId);
            }
            if (newParent.getStatus() != OrgUnitStatus.ACTIVE) {
                return status(HttpStatus.BAD_REQUEST, "Cannot move a unit under an inactive parent");
            }
            // Cycle guard: the new parent must not be the unit itself or any descendant. A descendant's
            // path starts with this unit's path.
            if (newParent.getPath().startsWith(unit.getPath())) {
                return status(HttpStatus.BAD_REQUEST,
                        "Cannot move a unit beneath itself or one of its descendants");
            }

            String oldPath = unit.getPath();
            String newPath = newParent.getPath() + unit.getId() + "/";
            int depthDelta = (newParent.getDepth() + 1) - unit.getDepth();

            // Load the whole subtree (unit + descendants) and rewrite paths/depths atomically.
            List<OrgUnit> subtree = orgUnitRepository.findSubtreeByPathPrefix(oldPath);
            for (OrgUnit node : subtree) {
                node.setPath(newPath + node.getPath().substring(oldPath.length()));
                node.setDepth(node.getDepth() + depthDelta);
                node.setUpdatedAt(LocalDateTime.now());
            }
            unit.setParent(newParent);
            orgUnitRepository.saveAll(subtree);

            OrgUnit refreshed = orgUnitRepository.findById(id).orElse(unit);
            return ok(HttpStatus.OK, "Organizational unit re-parented successfully", toResponse(refreshed, false));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // -------------------------------------------------------------- DEACTIVATE

    @Override
    @Audit(module = "ORG_UNIT", action = "DELETE")
    @Transactional
    public CustomResponse<?> deactivate(Long id) {
        try {
            OrgUnit unit = orgUnitRepository.findById(id).orElse(null);
            if (unit == null) {
                return status(HttpStatus.NOT_FOUND, "Organizational unit not found: " + id);
            }
            if (unit.getParent() == null) {
                return status(HttpStatus.BAD_REQUEST, "The root unit cannot be deactivated");
            }
            if (orgUnitRepository.countByParentIdAndStatus(id, OrgUnitStatus.ACTIVE) > 0) {
                return status(HttpStatus.CONFLICT,
                        "Deactivate or move the active child units first; a unit with active children cannot be deactivated");
            }
            // Historical references are preserved: we never hard-delete. The unit is flagged INACTIVE
            // so existing budget allocations/reports that reference it remain valid.
            unit.setStatus(OrgUnitStatus.INACTIVE);
            unit.setUpdatedAt(LocalDateTime.now());
            orgUnitRepository.save(unit);

            long refs = budgetLineRepository.countByOrgUnitId(id);
            String msg = refs > 0
                    ? "Organizational unit deactivated; " + refs + " historical budget allocation(s) preserved"
                    : "Organizational unit deactivated";
            return ok(HttpStatus.OK, msg, toResponse(unit, false));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // -------------------------------------------------------------------- READ

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getById(Long id) {
        OrgUnit unit = orgUnitRepository.findById(id).orElse(null);
        if (unit == null) {
            return status(HttpStatus.NOT_FOUND, "Organizational unit not found: " + id);
        }
        return ok(HttpStatus.OK, "Organizational unit fetched successfully", toResponse(unit, false));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getAll(boolean activeOnly) {
        List<OrgUnit> units = activeOnly
                ? orgUnitRepository.findByStatus(OrgUnitStatus.ACTIVE)
                : orgUnitRepository.findAll();
        List<OrgUnitResponse> body = units.stream()
                .sorted((a, b) -> a.getPath().compareTo(b.getPath()))
                .map(u -> toResponse(u, false))
                .collect(Collectors.toList());
        return ok(HttpStatus.OK, "Organizational units fetched successfully", body);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getTree() {
        List<OrgUnit> all = orgUnitRepository.findAll();
        if (all.isEmpty()) {
            return ok(HttpStatus.OK, "No organizational units", null);
        }
        Map<Long, OrgUnitResponse> byId = all.stream()
                .collect(Collectors.toMap(OrgUnit::getId, u -> toResponse(u, true)));
        OrgUnitResponse root = null;
        for (OrgUnit u : all) {
            OrgUnitResponse node = byId.get(u.getId());
            if (u.getParent() == null) {
                root = node;
            } else {
                OrgUnitResponse parent = byId.get(u.getParent().getId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return ok(HttpStatus.OK, "Organizational hierarchy fetched successfully", root);
    }

    // ----------------------------------------------------------------- HELPERS

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private User resolveHeadUser(Long headUserId) {
        if (headUserId == null) {
            return null;
        }
        return userRepository.findById(headUserId).orElse(null);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private OrgUnitResponse toResponse(OrgUnit u, boolean treeNode) {
        String headName = null;
        if (u.getHeadUser() != null) {
            User h = u.getHeadUser();
            headName = ((h.getFirstName() != null ? h.getFirstName() : "") + " "
                    + (h.getLastName() != null ? h.getLastName() : "")).trim();
            if (headName.isEmpty()) {
                headName = h.getEmail();
            }
        }
        return OrgUnitResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .code(u.getCode())
                .type(u.getType())
                .parentId(u.getParent() != null ? u.getParent().getId() : null)
                .path(u.getPath())
                .depth(u.getDepth())
                .status(u.getStatus())
                .headUserId(u.getHeadUser() != null ? u.getHeadUser().getId() : null)
                .headUserName(headName)
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .children(treeNode ? new ArrayList<>() : null)
                .build();
    }

    private CustomResponse<Object> ok(HttpStatus httpStatus, String message, Object entity) {
        CustomResponse<Object> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(entity);
        r.setStatusCode(httpStatus.value());
        return r;
    }

    private CustomResponse<Object> status(HttpStatus httpStatus, String message) {
        CustomResponse<Object> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(null);
        r.setStatusCode(httpStatus.value());
        return r;
    }

    private CustomResponse<Object> fail(Exception e) {
        CustomResponse<Object> r = new CustomResponse<>();
        if (e instanceof OptimisticLockException || e instanceof ObjectOptimisticLockingFailureException) {
            r.setMessage("The organizational unit was modified by another user. Please reload and try again.");
            r.setStatusCode(HttpStatus.CONFLICT.value());
        } else {
            r.setMessage(e.getMessage());
            r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        r.setEntity(null);
        return r;
    }
}
