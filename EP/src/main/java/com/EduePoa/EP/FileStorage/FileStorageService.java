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

/**
 * Stores uploaded files on the local filesystem and returns a web-accessible
 * path (NOT base64). Only the relative URL path is persisted in the database
 * (e.g. in {@code Tenant.logoUrl}); the binary bytes live on disk.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    /** Attachment types accepted for supporting documents (receipts, proofs, etc.). */
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "pdf", "doc", "docx");

    /** Maximum size for an uploaded supporting document (10 MB). */
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

    /**
     * Stores an uploaded image under the given sub-directory and returns the
     * public URL path (e.g. {@code /uploads/logos/<uuid>.png}) to persist.
     *
     * @param file          the uploaded multipart file
     * @param subDirectory  logical folder to group files (e.g. "logos")
     * @return the web path where the file can be served from
     */
    public String storeImage(MultipartFile file, String subDirectory) {
        return store(file, subDirectory, ALLOWED_IMAGE_EXTENSIONS, 0L, "image");
    }

    /**
     * Stores an uploaded supporting document (PNG/JPG/PDF/Word) under the given
     * sub-directory and returns the public URL path to persist. Enforces the
     * document type allow-list and a 10 MB size cap.
     *
     * @param file          the uploaded multipart file
     * @param subDirectory  logical folder to group files (e.g. "transaction-attachments")
     * @return the web path where the file can be served from
     */
    public String storeDocument(MultipartFile file, String subDirectory) {
        return store(file, subDirectory, ALLOWED_DOCUMENT_EXTENSIONS, MAX_DOCUMENT_SIZE_BYTES, "document");
    }

    /**
     * Shared store routine: validates emptiness, extension allow-list and (when
     * {@code maxSizeBytes > 0}) size, writes the bytes under a UUID filename in the
     * sanitised sub-directory, and returns the servable web path.
     */
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

    /**
     * Deletes a previously stored file given its public URL path. Silently
     * ignores files that no longer exist or paths outside the upload root.
     */
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
