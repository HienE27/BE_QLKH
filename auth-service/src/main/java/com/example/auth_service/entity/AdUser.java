package com.example.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.*;

@Entity
@Table(name = "ad_users")
@Data
public class AdUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    // các cột khác trong bảng ad_users (nếu cần thì map thêm)
    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "email_verification_token_expiry")
    @Temporal(TemporalType.TIMESTAMP)
    private Date emailVerificationTokenExpiry;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    @Temporal(TemporalType.TIMESTAMP)
    private Date passwordResetTokenExpiry;

    @Column(name = "phone")
    private String phone;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "address")
    private String address;

    @Column(name = "province")
    private String province;

    @Column(name = "district")
    private String district;

    @Column(name = "ward")
    private String ward;

    @Column(name = "country")
    private String country;

    @Column(name = "remember_token")
    private String rememberToken;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0; // mặc định 0 lần đăng nhập sai

    @Column(name = "account_locked_until")
    @Temporal(TemporalType.TIMESTAMP)
    private Date accountLockedUntil; // null nếu chưa bị khóa

    @Column(name = "unlock_token_hash")
    private String unlockTokenHash;

    @Column(name = "unlock_token_expiry")
    @Temporal(TemporalType.TIMESTAMP)
    private Date unlockTokenExpiry;

    @Column(name = "unlock_token_attempts")
    private Integer unlockTokenAttempts = 0;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "ad_user_has_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "roles_id")   
    )
    private Set<AdRole> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ad_user_has_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permissions_id")
    )
    private Set<AdPermission> permissions = new HashSet<>();
}