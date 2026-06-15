package com.portalcursos.ng02.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class StorageService {
    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    @Value("${app.upload.dir:uploads/documents}")
    private String uploadDir;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".pdf", ".jpg", ".jpeg", ".png", ".docx", ".doc");
    private static final List<String> ALLOWED_SUBFOLDERS = Arrays.asList(
        "fotos-perfil",
        "grad-students/rg", "grad-students/comprovante_residencia", "grad-students/certificado_em",
        "grad-students/historico_em", "grad-students/enem_sisu", "grad-students/diploma_ant",
        "grad-students/historico_ies_ant", "grad-students/laudo_medico", "grad-students/rnm_rne",
        "grad-students/titulo_eleitor", "grad-students/certificado_reservista",
        "grad-students/certidao_nascimento", "grad-students/autodeclaracao_racial",
        "postgrad/fotos-perfil", "postgrad/diplomas", "postgrad/documentos",
        "postgrad/residencia", "postgrad/historicos",
        "repairs-main", "repairs-gallery",
        "staff-photos",
        "documents", "repairs"
    );

    public String store(MultipartFile file, String subfolder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 1. Validação de Subfolder (Segurança OMEGA — evita path traversal)
        if (subfolder == null || !ALLOWED_SUBFOLDERS.contains(subfolder)) {
            logger.error("[SECURITY-ALERT] Tentativa de upload em subfolder não permitido: {}", subfolder);
            throw new IOException("Destino de upload inválido.");
        }

        // 2. Validação de Extensão (Segurança OMEGA)
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            logger.error("[SECURITY-ALERT] Tentativa de upload de arquivo não permitido: {}", extension);
            throw new IOException("Tipo de arquivo não permitido. Apenas PDF, Imagens e Word são aceitos.");
        }

        // 3. Preparação do Caminho
        Path uploadPath = Paths.get(uploadDir, subfolder);
        Files.createDirectories(uploadPath);

        // 4. Nome Único (Evita colisão e ataques de path traversal)
        String filename = UUID.randomUUID().toString() + extension;
        Path targetLocation = uploadPath.resolve(filename);

        logger.info("[STORAGE] Salvando arquivo {} em {}", filename, targetLocation);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return subfolder + "/" + filename;
    }

    public boolean delete(String filePath) {
        if (filePath == null || filePath.isBlank()) return false;
        try {
            // Proteção contra Path Traversal: Garante que o caminho não saia da pasta de uploads
            Path path = Paths.get(uploadDir, filePath).normalize();
            if (!path.startsWith(Paths.get(uploadDir).normalize())) {
                logger.warn("[SECURITY-WARN] Tentativa de deleção fora do diretório de uploads detectada!");
                return false;
            }
            boolean deleted = Files.deleteIfExists(path);
            if (!deleted) {
                logger.warn("[STORAGE-WARN] Arquivo não encontrado para deleção: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("[STORAGE-ERROR] Falha ao deletar arquivo: {}", filePath);
            return false;
        }
    }
}
