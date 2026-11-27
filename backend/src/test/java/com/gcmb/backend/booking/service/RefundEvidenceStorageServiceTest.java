package com.gcmb.backend.booking.service;

import com.gcmb.backend.booking.exception.BookingFailure;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundEvidenceStorageServiceTest {
    @TempDir
    Path storageDirectory;
    private RefundEvidenceStorageService storage;

    @BeforeEach
    void createLocalStorage() {
        storage = new RefundEvidenceStorageService(storageDirectory.toString(), "", "us-east-1", "", false);
    }

    @Test
    void savesAndReadsRefundEvidenceWithoutCloudStorage() throws IOException {
        byte[] image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aO1sAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("image", "minh-chung.png", "image/png", image);

        var evidence = storage.storeRefundEvidence(file);

        assertEquals("minh-chung.png", evidence.originalName());
        assertEquals("image/png", evidence.mimeType());
        assertArrayEquals(image, storage.read(evidence.objectKey()));
        storage.deleteQuietly(evidence.objectKey());
        assertThrows(IOException.class, () -> storage.read(evidence.objectKey()));
    }

    @Test
    void validatesImageContentBeforeSavingLocally() {
        var file = new MockMultipartFile("image", "minh-chung.png", "image/png", new byte[] {1, 2, 3});

        assertThrows(BookingFailure.class, () -> storage.storeRefundEvidence(file));
    }
}
