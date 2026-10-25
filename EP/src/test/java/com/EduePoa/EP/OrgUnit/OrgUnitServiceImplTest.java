package com.EduePoa.EP.OrgUnit;

import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitCreateRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitReparentRequest;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import com.EduePoa.EP.OrgUnit.Repository.OrgUnitRepository;
import com.EduePoa.EP.OrgUnit.Service.OrgUnitServiceImpl;
import com.EduePoa.EP.Utils.CustomResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tree-integrity tests for the organizational hierarchy: single-root enforcement, self-parenting
 * and move-beneath-descendant rejection, and atomic path/depth maintenance on re-parent
 * (design §5.2, §13).
 */
class OrgUnitServiceImplTest {

    private OrgUnitRepository repo;
    private OrgUnitServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(OrgUnitRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        BudgetLineRepository lineRepo = mock(BudgetLineRepository.class);
        lenient().when(lineRepo.countByOrgUnitId(any())).thenReturn(0L);
        service = new OrgUnitServiceImpl(repo, userRepo, lineRepo);
    }

    private OrgUnit unit(Long id, OrgUnit parent, String path, int depth) {
        return OrgUnit.builder().id(id).name("U" + id).code("U" + id).type("DEPARTMENT")
                .parent(parent).path(path).depth(depth).status(OrgUnitStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("Second root creation is rejected (single active root per tenant)")
    void singleRoot() {
        when(repo.existsByCode(anyString())).thenReturn(false);
        when(repo.findRoots(OrgUnitStatus.ACTIVE)).thenReturn(List.of(unit(1L, null, "/1/", 0)));

        CustomResponse<?> r = service.create(OrgUnitCreateRequest.builder()
                .name("Another Root").code("ROOT2").type("SCHOOL").parentId(null).build());

        assertThat(r.getStatusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("A unit cannot be re-parented onto itself")
    void noSelfParent() {
        OrgUnit root = unit(1L, null, "/1/", 0);
        OrgUnit child = unit(2L, root, "/1/2/", 1);
        when(repo.findById(2L)).thenReturn(Optional.of(child));

        CustomResponse<?> r = service.reparent(2L, OrgUnitReparentRequest.builder().newParentId(2L).build());
        assertThat(r.getStatusCode()).isEqualTo(400);
        assertThat(r.getMessage()).containsIgnoringCase("own parent");
    }

    @Test
    @DisplayName("A unit cannot be moved beneath one of its own descendants (cycle)")
    void noMoveBeneathDescendant() {
        OrgUnit root = unit(1L, null, "/1/", 0);
        OrgUnit a = unit(2L, root, "/1/2/", 1);
        OrgUnit descendant = unit(3L, a, "/1/2/3/", 2);
        when(repo.findById(2L)).thenReturn(Optional.of(a));
        when(repo.findById(3L)).thenReturn(Optional.of(descendant));

        CustomResponse<?> r = service.reparent(2L, OrgUnitReparentRequest.builder().newParentId(3L).build());
        assertThat(r.getStatusCode()).isEqualTo(400);
        assertThat(r.getMessage()).containsIgnoringCase("beneath");
    }

    @Test
    @DisplayName("Re-parent rewrites the moved unit and all descendants' paths and depths atomically")
    void reparentRewritesSubtree() {
        OrgUnit root = unit(1L, null, "/1/", 0);
        OrgUnit a = unit(2L, root, "/1/2/", 1);          // will move under b
        OrgUnit aChild = unit(4L, a, "/1/2/4/", 2);
        OrgUnit b = unit(3L, root, "/1/3/", 1);          // new parent

        when(repo.findById(2L)).thenReturn(Optional.of(a));
        when(repo.findById(3L)).thenReturn(Optional.of(b));
        when(repo.findSubtreeByPathPrefix("/1/2/")).thenReturn(List.of(a, aChild));
        when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reparent(2L, OrgUnitReparentRequest.builder().newParentId(3L).build());

        // a moves to /1/3/2/ depth 2; its child to /1/3/2/4/ depth 3.
        assertThat(a.getPath()).isEqualTo("/1/3/2/");
        assertThat(a.getDepth()).isEqualTo(2);
        assertThat(aChild.getPath()).isEqualTo("/1/3/2/4/");
        assertThat(aChild.getDepth()).isEqualTo(3);
        assertThat(a.getParent().getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Deactivating a unit with active children is rejected")
    void deactivateWithActiveChildrenRejected() {
        OrgUnit root = unit(1L, null, "/1/", 0);
        OrgUnit a = unit(2L, root, "/1/2/", 1);
        when(repo.findById(2L)).thenReturn(Optional.of(a));
        when(repo.countByParentIdAndStatus(2L, OrgUnitStatus.ACTIVE)).thenReturn(1L);

        CustomResponse<?> r = service.deactivate(2L);
        assertThat(r.getStatusCode()).isEqualTo(409);
    }
}
