package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Authentication.User.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "parent")
public class Parent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String otherNames;

    @Column(unique = true)
    private String phoneNumber;

    private String alternatePhoneNumber;

    @Column(unique = true)
    private String email;

    private String nationalIdOrPassport;
    private String occupation;
    private String address;

    private boolean portalAccessEnabled = false;
    private boolean receiveSms = true;
    private boolean receiveEmail = false;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @CreationTimestamp
    private Timestamp createdOn;

    private char deletedFlag = 'N';
}
