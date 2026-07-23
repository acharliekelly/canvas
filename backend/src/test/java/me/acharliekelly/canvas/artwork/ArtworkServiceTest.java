package me.acharliekelly.canvas.artwork;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import me.acharliekelly.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ArtworkServiceTest {
    @Mock
    ArtworkRepository repository;

    @Mock
    ObjectStorage storage;

    @Mock
    PlatformTransactionManager transactionManager;

    @Test
    void persistenceFailureDeletesStoredObject() throws Exception {
        when(storage.put(any(), anyLong(), anyString()))
                .thenReturn(new ObjectStorage.StoredObject("artworks/stored-object"));
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ArtworkService service = new ArtworkService(repository, storage, transactionManager, DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> service.upload(validPng(), "Blue Study", "A. Artist", null))
                .isInstanceOf(ArtworkService.ArtworkProblem.class)
                .hasMessage("Artwork metadata could not be saved.");

        verify(storage).delete("artworks/stored-object");
    }

    private static MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray());
    }
}
