package stirling.software.SPDF.controller.api.converters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.config.RuntimePathConfig;
import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.model.api.converters.HTMLToPdfRequest;
import stirling.software.SPDF.service.CustomPDFDocumentFactory;
import stirling.software.SPDF.utils.FileToPdf;

@ExtendWith(MockitoExtension.class)
public class ConvertHtmlToPDFTest {

    @Mock private CustomPDFDocumentFactory mockPdfDocumentFactory;
    @Mock private ApplicationProperties mockApplicationProperties;
    @Mock private ApplicationProperties.System mockSystem;
    @Mock private RuntimePathConfig mockRuntimePathConfig;

    private ConvertHtmlToPDF convertHtmlToPDF;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        lenient().when(mockApplicationProperties.getSystem()).thenReturn(mockSystem);
        lenient().when(mockRuntimePathConfig.getWeasyPrintPath()).thenReturn("weasyprint");

        convertHtmlToPDF =
                new ConvertHtmlToPDF(
                        mockPdfDocumentFactory, mockApplicationProperties, mockRuntimePathConfig);
    }

    @Test
    void testHtmlToPdf_WithNullFileInput_ThrowsException() {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        request.setFileInput(null);

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> convertHtmlToPDF.HtmlToPdf(request));

        assertEquals("Please provide an HTML or ZIP file for conversion.", exception.getMessage());
    }

    @Test
    void testHtmlToPdf_WithInvalidFileExtension_ThrowsException() {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        MultipartFile invalidFile =
                new MockMultipartFile(
                        "file", "test.txt", "text/plain", "invalid content".getBytes());
        request.setFileInput(invalidFile);

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> convertHtmlToPDF.HtmlToPdf(request));

        assertEquals("File must be either .html or .zip format.", exception.getMessage());
    }

    @Test
    void testHtmlToPdf_WithHtmlFile_Success() throws Exception {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        MultipartFile htmlFile =
                new MockMultipartFile(
                        "file",
                        "test.html",
                        "text/html",
                        "<html><body>Test content</body></html>".getBytes());
        request.setFileInput(htmlFile);
        request.setZoom(1.0f);

        byte[] mockPdfBytes = "PDF content".getBytes();
        byte[] mockProcessedPdfBytes = "Processed PDF content".getBytes();

        // Mock system property for sanitization
        when(mockSystem.getDisableSanitize()).thenReturn(false);

        // Mock FileToPdf static method using mockito-inline
        try (var fileToPdfMock = mockStatic(FileToPdf.class)) {
            fileToPdfMock
                    .when(
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            anyString(),
                                            any(HTMLToPdfRequest.class),
                                            any(byte[].class),
                                            eq("test.html"),
                                            eq(false)))
                    .thenReturn(mockPdfBytes);

            // Mock document factory processing
            when(mockPdfDocumentFactory.createNewBytesBasedOnOldDocument(mockPdfBytes))
                    .thenReturn(mockProcessedPdfBytes);

            // Act
            ResponseEntity<byte[]> response = convertHtmlToPDF.HtmlToPdf(request);

            // Assert
            assertEquals(200, response.getStatusCodeValue());
            assertArrayEquals(mockProcessedPdfBytes, response.getBody());
            assertEquals(
                    "form-data; name=\"attachment\"; filename=\"test.pdf\"",
                    Objects.requireNonNull(response.getHeaders().getContentDisposition())
                            .toString());

            // Verify interactions
            fileToPdfMock.verify(
                    () ->
                            FileToPdf.convertHtmlToPdf(
                                    anyString(),
                                    any(HTMLToPdfRequest.class),
                                    any(byte[].class),
                                    eq("test.html"),
                                    eq(false)));
            verify(mockPdfDocumentFactory).createNewBytesBasedOnOldDocument(mockPdfBytes);
        }
    }

    @Test
    void testHtmlToPdf_WithZipFile_Success() throws Exception {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        MultipartFile zipFile =
                new MockMultipartFile(
                        "file", "test.zip", "application/zip", "ZIP content".getBytes());
        request.setFileInput(zipFile);
        request.setZoom(1.5f);

        byte[] mockPdfBytes = "PDF content".getBytes();
        byte[] mockProcessedPdfBytes = "Processed PDF content".getBytes();

        // Mock system property for sanitization
        when(mockSystem.getDisableSanitize()).thenReturn(true);

        // Mock FileToPdf static method using mockito-inline
        try (var fileToPdfMock = mockStatic(FileToPdf.class)) {
            fileToPdfMock
                    .when(
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            anyString(),
                                            any(HTMLToPdfRequest.class),
                                            any(byte[].class),
                                            eq("test.zip"),
                                            eq(true)))
                    .thenReturn(mockPdfBytes);

            // Mock document factory processing
            when(mockPdfDocumentFactory.createNewBytesBasedOnOldDocument(mockPdfBytes))
                    .thenReturn(mockProcessedPdfBytes);

            // Act
            ResponseEntity<byte[]> response = convertHtmlToPDF.HtmlToPdf(request);

            // Assert
            assertEquals(200, response.getStatusCodeValue());
            assertArrayEquals(mockProcessedPdfBytes, response.getBody());
            assertEquals(
                    "form-data; name=\"attachment\"; filename=\"test.pdf\"",
                    Objects.requireNonNull(response.getHeaders().getContentDisposition())
                            .toString());

            // Verify interactions
            fileToPdfMock.verify(
                    () ->
                            FileToPdf.convertHtmlToPdf(
                                    anyString(),
                                    any(HTMLToPdfRequest.class),
                                    any(byte[].class),
                                    eq("test.zip"),
                                    eq(true)));
            verify(mockPdfDocumentFactory).createNewBytesBasedOnOldDocument(mockPdfBytes);
        }
    }

    @Test
    void testHtmlToPdf_WithNoFileExtension_ThrowsException() {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        MultipartFile invalidFile =
                new MockMultipartFile(
                        "file", "testWithoutExtension", "text/plain", "content".getBytes());
        request.setFileInput(invalidFile);

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> convertHtmlToPDF.HtmlToPdf(request));

        assertEquals("File must be either .html or .zip format.", exception.getMessage());
    }

    @Test
    void testHtmlToPdf_WithMaliciousFilename_HandlesSecurely() throws Exception {
        // Arrange
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        MultipartFile htmlFile =
                new MockMultipartFile(
                        "file",
                        "../malicious/path/test.html",
                        "text/html",
                        "<html><body>Test content</body></html>".getBytes());
        request.setFileInput(htmlFile);

        byte[] mockPdfBytes = "PDF content".getBytes();
        byte[] mockProcessedPdfBytes = "Processed PDF content".getBytes();

        // Mock system property for sanitization
        when(mockSystem.getDisableSanitize()).thenReturn(false);

        // Mock FileToPdf static method using mockito-inline
        try (var fileToPdfMock = mockStatic(FileToPdf.class)) {
            fileToPdfMock
                    .when(
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            anyString(),
                                            any(HTMLToPdfRequest.class),
                                            any(byte[].class),
                                            eq("test.html"), // Expect sanitized filename
                                            eq(false)))
                    .thenReturn(mockPdfBytes);

            // Mock document factory processing
            when(mockPdfDocumentFactory.createNewBytesBasedOnOldDocument(mockPdfBytes))
                    .thenReturn(mockProcessedPdfBytes);

            // Act
            ResponseEntity<byte[]> response = convertHtmlToPDF.HtmlToPdf(request);

            // Assert
            assertEquals(200, response.getStatusCodeValue());
            assertEquals(
                    "form-data; name=\"attachment\"; filename=\"test.pdf\"",
                    Objects.requireNonNull(response.getHeaders().getContentDisposition())
                            .toString());
        }
    }
}
