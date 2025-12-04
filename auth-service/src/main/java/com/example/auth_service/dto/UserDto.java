// package com.example.auth_service.dto;

// import com.example.auth_service.entity.AdUser;
// import lombok.Data;

// import java.util.List;

// @Data
// public class UserDto {
//     private Long id;
//     private String username;
//     private String fullName;
//     private String email;
//     private Boolean active;
//     private List<String> roles;

//     public static UserDto fromEntity(AdUser u) {
//         UserDto dto = new UserDto();
//         dto.setId(u.getId());
//         dto.setUsername(u.getUsername());
//         dto.setFullName(
//                 (u.getFirstName() != null ? u.getFirstName() : "") + " " +
//                         (u.getLastName() != null ? u.getLastName() : "")
//         );
//         dto.setEmail(u.getEmail());
//         dto.setActive(u.getActive());

//         dto.setRoles(
//                 u.getRoles() != null
//                         ? u.getRoles().stream().map(r -> r.getCode()).toList()
//                         : List.of()
//         );
//         return dto;
//     }
// }