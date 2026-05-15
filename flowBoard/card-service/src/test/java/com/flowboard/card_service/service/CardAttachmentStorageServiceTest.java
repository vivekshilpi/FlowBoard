package com.flowboard.card_service.service;

import com.flowboard.card_service.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardAttachmentStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeReadAndDeleteRoundTrip() throws Exception {
        CardAttachmentStorageService service = new CardAttachmentStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        CardAttachmentStorageService.StoredAttachment stored = service.store(1L, file);

        assertThat(Files.exists(Path.of(stored.storedPath()))).isTrue();
        assertThat(service.read(stored.storedPath())).isEqualTo("hello".getBytes());

        service.deleteQuietly(stored.storedPath());
        assertThat(Files.exists(Path.of(stored.storedPath()))).isFalse();
    }

    @Test
    void storeRejectsMissingFile() {
        CardAttachmentStorageService service = new CardAttachmentStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.store(1L, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void storeRejectsInvalidExtension() {
        CardAttachmentStorageService service = new CardAttachmentStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", "oops".getBytes());

        assertThatThrownBy(() -> service.store(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void storeRejectsOversizedFile() {
        CardAttachmentStorageService service = new CardAttachmentStorageService(tempDir.toString());
        byte[] large = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", large);

        assertThatThrownBy(() -> service.store(1L, file))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void readMissingFileThrowsNotFound() {
        CardAttachmentStorageService service = new CardAttachmentStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.read(tempDir.resolve("missing.txt").toString()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
