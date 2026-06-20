package com.omakase.kok.user.presentation.user.dto.request;

import com.omakase.kok.user.domain.user.enums.Role;
import jakarta.validation.constraints.*;

public record SignupRequest(

        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 100, message = "아이디는 100자 이하로 입력해주세요.")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[!@#$%^&*()_+=\\-{}\\[\\]:;\"'<>,.?/]).+$",
                message = "비밀번호는 대문자와 특수문자를 각각 1개 이상 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 20, message = "이름은 20자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Size(max = 20, message = "전화번호는 20자 이하로 입력해주세요.")
        String phone,

        @Size(max = 100, message = "Slack ID는 100자 이하로 입력해주세요.")
        String slackId,

        @NotNull(message = "권한은 필수입니다.")
        Role role
) {
}
