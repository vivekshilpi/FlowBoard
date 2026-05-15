package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.exception.CustomException;
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
public class AvatarStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;

    private final Path rootDirectory;

    public AvatarStorageService(@Value("${app.avatar.upload-dir:uploads/avatars}") String uploadDir) {
        this.rootDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storeAvatar(Long userId, MultipartFile file) {
        validate(file);

        String extension = getExtension(file.getOriginalFilename());
        String filename = userId + "-" + UUID.randomUUID() + "." + extension;
        Path userDirectory = rootDirectory.resolve(String.valueOf(userId));
        Path destination = userDirectory.resolve(filename);

        try {
            Files.createDirectories(userDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CustomException("Failed to store avatar image", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return "/uploads/avatars/" + userId + "/" + filename;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Avatar file is required", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException("Avatar file must be 2 MB or smaller", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException("Only JPG and PNG avatar images are allowed", HttpStatus.BAD_REQUEST);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException("Avatar file extension must be JPG or PNG", HttpStatus.BAD_REQUEST);
        }
    }

    private String getExtension(String filename) {
        String cleanedFilename = StringUtils.cleanPath(filename == null ? "" : filename);
        int lastDotIndex = cleanedFilename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == cleanedFilename.length() - 1) {
            throw new CustomException("Avatar file must include a valid extension", HttpStatus.BAD_REQUEST);
        }

        return cleanedFilename.substring(lastDotIndex + 1).toLowerCase();
    }
}
