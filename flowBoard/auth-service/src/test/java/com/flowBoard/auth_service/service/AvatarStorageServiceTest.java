package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAvatar_savesFileAndReturnsPublicPath() throws Exception {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3});

        String path = service.storeAvatar(9L, file);

        assertThat(path).startsWith("/uploads/avatars/9/");
        assertThat(Files.list(tempDir.resolve("9"))).hasSize(1);
    }

    @Test
    void storeAvatar_rejectsEmptyFile() {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.storeAvatar(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void storeAvatar_rejectsUnsupportedType() {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[] {1});

        assertThatThrownBy(() -> service.storeAvatar(1L, file))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Only JPG and PNG");
    }

    @Test
    void storeAvatar_rejectsMissingExtension() {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.storeAvatar(1L, file))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("valid extension");
    }

    @Test
    void storeAvatar_rejectsOversizedFile() {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        byte[] bytes = new byte[(2 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", bytes);

        assertThatThrownBy(() -> service.storeAvatar(1L, file))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("2 MB or smaller");
    }

    @Test
    void storeAvatar_wrapsIoFailure() throws Exception {
        Path blockingFile = Files.createTempFile(tempDir, "block", ".tmp");
        AvatarStorageService service = new AvatarStorageService(blockingFile.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.storeAvatar(1L, file))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Failed to store avatar image");
    }
}
