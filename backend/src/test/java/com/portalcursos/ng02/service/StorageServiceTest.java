package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** B9 da auditoria: o conteúdo do upload precisa bater com a extensão (magic bytes). */
class StorageServiceTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0};
    private static final byte[] DOC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] EXE = {0x4D, 0x5A, (byte) 0x90, 0, 3, 0, 0, 0};

    @TempDir
    Path dir;

    private StorageService service() {
        StorageService s = new StorageService();
        ReflectionTestUtils.setField(s, "uploadDir", dir.toString());
        return s;
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("f", name, "application/octet-stream", content);
    }

    @Test
    void aceitaArquivosCujoConteudoBateComAExtensao() throws IOException {
        StorageService s = service();
        for (Object[] c : new Object[][] {{"a.pdf", PDF}, {"a.png", PNG}, {"a.jpg", JPG}, {"a.JPEG", JPG}, {"a.docx", ZIP}, {"a.doc", DOC}}) {
            assertNotNull(s.store(file((String) c[0], (byte[]) c[1]), "documents"), (String) c[0]);
        }
    }

    @Test
    void recusaExecutavelRenomeadoEConteudoVazioOuCurto() {
        StorageService s = service();
        for (Object[] c : new Object[][] {{"a.pdf", EXE}, {"a.png", JPG}, {"a.jpg", PNG}, {"a.docx", PDF}, {"a.pdf", new byte[] {0x25}}}) {
            IOException ex = assertThrows(IOException.class, () -> s.store(file((String) c[0], (byte[]) c[1]), "documents"));
            assertEquals("O conteúdo do arquivo não corresponde ao tipo informado.", ex.getMessage());
        }
        assertEquals(0, dir.resolve("documents").toFile().exists() ? dir.resolve("documents").toFile().list().length : 0);
    }
}
