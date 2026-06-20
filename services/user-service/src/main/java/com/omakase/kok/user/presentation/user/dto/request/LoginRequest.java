package com.omakase.kok.user.presentation.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public final class LoginRequest {
   @NotBlank(message = "아이디는 필수입니다.")
    private String username;

   @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
