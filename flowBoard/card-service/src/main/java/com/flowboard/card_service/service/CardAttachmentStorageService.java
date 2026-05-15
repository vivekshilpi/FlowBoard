package com.flowboard.card_service.service;

import com.flowboard.card_service.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class CardAttachmentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt"
    );
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private final Path rootDirectory;

    public CardAttachmentStorageService(
            @Value("${app.card-attachments.upload-dir:uploads/card-attachments}") String uploadDir) {
        this.rootDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredAttachment store(Long cardId, MultipartFile file) {
        validate(file);

        String extension = getExtension(file.getOriginalFilename());
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "attachment." + extension
                : file.getOriginalFilename());
        String filename = UUID.randomUUID() + "." + extension;
        Path cardDirectory = rootDirectory.resolve(String.valueOf(cardId));
        Path destination = cardDirectory.resolve(filename);

        try {
            Files.createDirectories(cardDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CustomException("Failed to store attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new StoredAttachment(
                destination.toString(),
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize()
        );
    }

    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(Path.of(storedPath));
        } catch (IOException e) {
            throw new CustomException("Attachment file is unavailable", HttpStatus.NOT_FOUND);
        }
    }

    public void deleteQuietly(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException ignored) {
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Attachment file is required", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException("Attachment must be 10 MB or smaller", HttpStatus.BAD_REQUEST);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(
                    "Allowed attachment types: JPG, PNG, GIF, WEBP, PDF, DOC, DOCX, TXT",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String getExtension(String filename) {
        String cleanedFilename = StringUtils.cleanPath(filename == null ? "" : filename);
        int lastDotIndex = cleanedFilename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == cleanedFilename.length() - 1) {
            throw new CustomException("Attachment file must include a valid extension", HttpStatus.BAD_REQUEST);
        }

        return cleanedFilename.substring(lastDotIndex + 1).toLowerCase();
    }

    public record StoredAttachment(String storedPath, String originalName, String contentType, long size) {
    }
}
