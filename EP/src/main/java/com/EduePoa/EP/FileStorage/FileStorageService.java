package com.EduePoa.EP.FileStorage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "pdf", "doc", "docx");

    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024;

    private final Path uploadRoot;
    private final String urlPrefix;

    public FileStorageService(
            @Value("${file.upload.dir:uploads}") String uploadDir,
            @Value("${file.upload.url-prefix:/uploads}") String urlPrefix) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        // normalise the prefix to a leading slash, no trailing slash
        String normalized = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        this.urlPrefix = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(uploadRoot);
            log.info("File upload root ready at {}", uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadRoot, e);
        }
    }


    public String storeImage(MultipartFile file, String subDirectory) {
        return store(file, subDirectory, ALLOWED_IMAGE_EXTENSIONS, 0L, "image");
    }


    public String storeDocument(MultipartFile file, String subDirectory) {
        return store(file, subDirectory, ALLOWED_DOCUMENT_EXTENSIONS, MAX_DOCUMENT_SIZE_BYTES, "document");
    }

    private String store(MultipartFile file, String subDirectory, Set<String> allowedExtensions,
                         long maxSizeBytes, String kind) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum allowed size of " + (maxSizeBytes / (1024 * 1024)) + " MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported " + kind + " type '" + extension + "'. Allowed: " + allowedExtensions);
        }

        String safeSubDir = sanitizeSubDirectory(subDirectory);
        String filename = UUID.randomUUID() + "." + extension;

        try {
            Path targetDir = uploadRoot.resolve(safeSubDir).normalize();
            // Guard against path traversal
            if (!targetDir.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid storage location");
            }
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String webPath = urlPrefix + "/" + safeSubDir + "/" + filename;
            log.info("Stored upload {} -> {}", file.getOriginalFilename(), webPath);
            return webPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file " + filename, e);
        }
    }


    public void deleteByWebPath(String webPath) {
        if (!StringUtils.hasText(webPath) || !webPath.startsWith(urlPrefix + "/")) {
            return;
        }
        String relative = webPath.substring((urlPrefix + "/").length());
        try {
            Path target = uploadRoot.resolve(relative).normalize();
            if (target.startsWith(uploadRoot)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("Could not delete file for path {}: {}", webPath, e.getMessage());
        }
    }

    private static String getExtension(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeSubDirectory(String subDirectory) {
        if (!StringUtils.hasText(subDirectory)) {
            return "misc";
        }
        // Only allow simple folder names, strip any traversal characters
        String cleaned = subDirectory.replaceAll("[^a-zA-Z0-9._-]", "");
        return StringUtils.hasText(cleaned) ? cleaned : "misc";
    }
}
