package com.EduePoa.EP.Authentication.User;

import com.EduePoa.EP.Authentication.User.Request.UserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("api/v1/user/")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final  UserService userService;
    @PostMapping("create")
    ResponseEntity<?>create(@RequestBody UserRequest userRequest){
        var response = userService.create(userRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);

    }


}
