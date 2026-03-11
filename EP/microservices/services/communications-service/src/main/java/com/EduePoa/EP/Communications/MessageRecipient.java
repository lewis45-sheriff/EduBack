package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Communications.Enums.DeliveryStatus;
import com.EduePoa.EP.Communications.Enums.RecipientType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "message_recipients")
public class MessageRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientType recipientType;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private String recipientName;

    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    private LocalDateTime deliveredAt;

    private LocalDateTime readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    @JsonIgnore
    private Message message;
}
