package com.portalcursos.ng02.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${app.upload.dir:uploads/documents}")
    private String uploadDir;

    public String store(MultipartFile file, String subfolder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        Path uploadPath = Paths.get(uploadDir, subfolder);
        Files.createDirectories(uploadPath);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID().toString() + extension;
        Path targetLocation = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return subfolder + "/" + filename;
    }

    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        try {
            Path path = Paths.get(uploadDir, filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log silencioso - arquivo pode já ter sido removido
        }
    }
}
