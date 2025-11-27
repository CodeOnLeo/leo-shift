package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 1, max = 50, message = "닉네임은 1-50자 사이여야 합니다")
    private String nickname;
}
