package com.EduePoa.EP.Authentication.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
   Optional< User> findByUsername(String usename);
    Optional<User> findByEmail(String email);
    @Query(nativeQuery = true, value = "Select count(*) from user join role r on r.id = user.role_id where r.name=:roleName")
    Integer adminCount(@Param("roleName") String roleName);


}
