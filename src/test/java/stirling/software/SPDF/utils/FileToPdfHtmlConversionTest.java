package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.SPDF.model.api.converters.HTMLToPdfRequest;

@ExtendWith(MockitoExtension.class)
public class FileToPdfHtmlConversionTest {

    private final String WEASYPRINT_PATH = "weasyprint";
    private HTMLToPdfRequest request;
    private ProcessExecutor mockProcessExecutor;

    @BeforeEach
    void setUp() {
        request = new HTMLToPdfRequest();
        request.setZoom(1.0f);

        mockProcessExecutor = mock(ProcessExecutor.class);
    }

    @Test
    void testConvertHtmlToPdf_WithValidHtmlContent_Success() throws Exception {
        // Arrange
        String htmlContent = "<html><body><h1>Test HTML Content</h1></body></html>";
        byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = false;

        String sanitizedHtml = "<html><body><h1>Sanitized HTML Content</h1></body></html>";

        // Create a temporary PDF file to be "produced" by the process
        Path tempPdfPath = Files.createTempFile("output_", ".pdf");
        Files.write(tempPdfPath, "PDF Content".getBytes());

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        mockStatic(ProcessExecutor.class)) {

            // Mock temporary file creation
            Path mockInputFile = mock(Path.class);
            Path mockOutputFile = tempPdfPath;

            mockedFiles
                    .when(() -> Files.createTempFile(eq("input_"), eq(".html")))
                    .thenReturn(mockInputFile);
            mockedFiles
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(mockOutputFile);

            // Allow the real Files.readAllBytes for the output file
            mockedFiles.when(() -> Files.readAllBytes(mockOutputFile)).thenCallRealMethod();

            // Mock the sanitizer
            mockedSanitizer
                    .when(() -> CustomHtmlSanitizer.sanitize(anyString()))
                    .thenReturn(sanitizedHtml);

            // Mock writing to the input file
            //            doNothing()
            //                    .when(mockedFiles)
            //                    .when(() -> Files.write(eq(mockInputFile), any(byte[].class)));
            mockedFiles
                    .when(() -> Files.write(eq(mockInputFile), any(byte[].class)))
                    .thenReturn(mockInputFile);

            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

            // Mock the process execution
            ProcessExecutor mockExecutorInstance = mock(ProcessExecutor.class);
            // Create a ProcessExecutorResult properly
            Object successResult = createSuccessResult(mockExecutorInstance);

            mockedProcessExecutor
                    .when(
                            () ->
                                    ProcessExecutor.getInstance(
                                            eq(ProcessExecutor.Processes.WEASYPRINT)))
                    .thenReturn(mockExecutorInstance);

            when(mockExecutorInstance.runCommandWithOutputHandling(
                            argThat(
                                    commands ->
                                            commands.contains(WEASYPRINT_PATH)
                                                    && commands.contains(
                                                            mockInputFile.toString()))))
                    .thenReturn((ProcessExecutor.ProcessExecutorResult) successResult);

            // Mock file deletion
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            // Act
            byte[] result =
                    FileToPdf.convertHtmlToPdf(
                            WEASYPRINT_PATH, request, htmlBytes, fileName, disableSanitize);

            // Assert
            assertNotNull(result);
            assertEquals("PDF Content", new String(result));

            // Verify
            mockedSanitizer.verify(() -> CustomHtmlSanitizer.sanitize(anyString()));
            mockedFiles.verify(() -> Files.write(eq(mockInputFile), any(byte[].class)));
            mockedFiles.verify(() -> Files.deleteIfExists(mockInputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockOutputFile));
        }
    }

    @Test
    void testConvertHtmlToPdf_WithDisabledSanitization_Success() throws Exception {
        // Arrange
        String htmlContent = "<html><body><h1>Test HTML Content</h1></body></html>";
        byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = true; // Sanitization disabled

        // Create a temporary PDF file to be "produced" by the process
        Path tempPdfPath = Files.createTempFile("output_", ".pdf");
        Files.write(tempPdfPath, "PDF Content".getBytes());

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        mockStatic(ProcessExecutor.class)) {

            // Mock temporary file creation
            Path mockInputFile = mock(Path.class);
            Path mockOutputFile = tempPdfPath;

            mockedFiles
                    .when(() -> Files.createTempFile(eq("input_"), eq(".html")))
                    .thenReturn(mockInputFile);
            mockedFiles
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(mockOutputFile);

            // Allow the real Files.readAllBytes for the output file
            mockedFiles.when(() -> Files.readAllBytes(mockOutputFile)).thenCallRealMethod();

            // Mock writing to the input file
            doNothing()
                    .when(mockedFiles)
                    .when(() -> Files.write(eq(mockInputFile), any(byte[].class)));

            // Mock the process execution
            ProcessExecutor mockExecutorInstance = mock(ProcessExecutor.class);
            // Create a ProcessExecutorResult properly
            Object successResult = createSuccessResult(mockExecutorInstance);

            mockedProcessExecutor
                    .when(
                            () ->
                                    ProcessExecutor.getInstance(
                                            eq(ProcessExecutor.Processes.WEASYPRINT)))
                    .thenReturn(mockExecutorInstance);

            when(mockExecutorInstance.runCommandWithOutputHandling(
                            argThat(
                                    commands ->
                                            commands.contains(WEASYPRINT_PATH)
                                                    && commands.contains(
                                                            mockInputFile.toString()))))
                    .thenReturn((ProcessExecutor.ProcessExecutorResult) successResult);

            // Mock file deletion
            doNothing().when(mockedFiles).when(() -> Files.deleteIfExists(any(Path.class)));

            // Act
            byte[] result =
                    FileToPdf.convertHtmlToPdf(
                            WEASYPRINT_PATH, request, htmlBytes, fileName, disableSanitize);

            // Assert
            assertNotNull(result);
            assertEquals("PDF Content", new String(result));

            // Verify sanitizer was NOT called
            mockedSanitizer.verify(() -> CustomHtmlSanitizer.sanitize(anyString()), never());
        }
    }

    @Test
    void testConvertHtmlToPdf_WithZipFile_ProcessesCorrectly() throws Exception {
        // Arrange
        byte[] zipBytes = createTestZipWithHtml();
        String fileName = "test.zip";
        boolean disableSanitize = false;

        // Create a temporary PDF file to be "produced" by the process
        Path tempPdfPath = Files.createTempFile("output_", ".pdf");
        Files.write(tempPdfPath, "ZIP PDF Content".getBytes());

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        mockStatic(ProcessExecutor.class)) {

            // Mock temporary file creation
            Path mockInputFile = mock(Path.class);
            Path mockOutputFile = tempPdfPath;
            Path mockTempDir = mock(Path.class);

            mockedFiles
                    .when(() -> Files.createTempFile(eq("input_"), eq(".zip")))
                    .thenReturn(mockInputFile);
            mockedFiles
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(mockOutputFile);
            mockedFiles.when(() -> Files.createTempDirectory(anyString())).thenReturn(mockTempDir);

            // Allow the real Files.readAllBytes for the output file
            mockedFiles.when(() -> Files.readAllBytes(mockOutputFile)).thenCallRealMethod();

            // Mock the ZIP handling methods
            doNothing()
                    .when(mockedFiles)
                    .when(() -> Files.write(any(Path.class), any(byte[].class)));

            // Mock the process execution
            ProcessExecutor mockExecutorInstance = mock(ProcessExecutor.class);
            // Create a ProcessExecutorResult properly
            Object successResult = createSuccessResult(mockExecutorInstance);

            mockedProcessExecutor
                    .when(
                            () ->
                                    ProcessExecutor.getInstance(
                                            eq(ProcessExecutor.Processes.WEASYPRINT)))
                    .thenReturn(mockExecutorInstance);

            when(mockExecutorInstance.runCommandWithOutputHandling(
                            argThat(
                                    commands ->
                                            commands.contains(WEASYPRINT_PATH)
                                                    && commands.contains(
                                                            mockInputFile.toString()))))
                    .thenReturn((ProcessExecutor.ProcessExecutorResult) successResult);

            // Mock file deletion
            doNothing().when(mockedFiles).when(() -> Files.deleteIfExists(any(Path.class)));

            // Act
            byte[] result =
                    FileToPdf.convertHtmlToPdf(
                            WEASYPRINT_PATH, request, zipBytes, fileName, disableSanitize);

            // Assert
            assertNotNull(result);
            assertEquals("ZIP PDF Content", new String(result));
        }
    }

    @Test
    void testConvertHtmlToPdf_WithInvalidFileName_ThrowsException() {
        // Arrange
        byte[] fileBytes = "test content".getBytes();
        String fileName = "test.invalid"; // Not HTML or ZIP
        boolean disableSanitize = false;

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                FileToPdf.convertHtmlToPdf(
                                        WEASYPRINT_PATH,
                                        request,
                                        fileBytes,
                                        fileName,
                                        disableSanitize));

        assertEquals("Unsupported file format: test.invalid", exception.getMessage());
    }

    @Test
    void testConvertHtmlToPdf_WithProcessFailure_HandlesError() throws Exception {
        // Arrange
        String htmlContent = "<html><body><h1>Test HTML Content</h1></body></html>";
        byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = false;

        // Create a temporary PDF file
        Path tempPdfPath = Files.createTempFile("output_", ".pdf");
        Files.write(tempPdfPath, new byte[0]); // Empty PDF to simulate error

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        mockStatic(ProcessExecutor.class)) {

            // Mock temporary file creation
            Path mockInputFile = mock(Path.class);
            Path mockOutputFile = tempPdfPath;

            mockedFiles
                    .when(() -> Files.createTempFile(eq("input_"), eq(".html")))
                    .thenReturn(mockInputFile);
            mockedFiles
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(mockOutputFile);

            // Allow real method call for file size check
            mockedFiles.when(() -> Files.readAllBytes(mockOutputFile)).thenCallRealMethod();

            // Mock sanitizer
            mockedSanitizer
                    .when(() -> CustomHtmlSanitizer.sanitize(anyString()))
                    .thenReturn(htmlContent);

            // Mock writing to the input file
            doNothing()
                    .when(mockedFiles)
                    .when(() -> Files.write(eq(mockInputFile), any(byte[].class)));

            // Mock process execution failure
            ProcessExecutor mockExecutorInstance = mock(ProcessExecutor.class);

            mockedProcessExecutor
                    .when(
                            () ->
                                    ProcessExecutor.getInstance(
                                            eq(ProcessExecutor.Processes.WEASYPRINT)))
                    .thenReturn(mockExecutorInstance);

            when(mockExecutorInstance.runCommandWithOutputHandling(any()))
                    .thenThrow(new IOException("Process failed"));

            // Act & Assert
            IOException exception =
                    assertThrows(
                            IOException.class,
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            WEASYPRINT_PATH,
                                            request,
                                            htmlBytes,
                                            fileName,
                                            disableSanitize));

            assertEquals("Process failed", exception.getMessage());

            // Verify cleanup still occurs
            mockedFiles.verify(() -> Files.deleteIfExists(mockInputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockOutputFile));
        }
    }

    private byte[] createTestZipWithHtml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add an HTML file
            ZipEntry htmlEntry = new ZipEntry("index.html");
            zos.putNextEntry(htmlEntry);
            zos.write(
                    "<html><body><h1>Test HTML in ZIP</h1></body></html>"
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add a CSS file
            ZipEntry cssEntry = new ZipEntry("styles.css");
            zos.putNextEntry(cssEntry);
            zos.write("h1 { color: blue; }".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Helper method to create a properly mocked ProcessExecutorResult that avoids directly using
     * the inner class
     */
    private ProcessExecutor.ProcessExecutorResult createSuccessResult(ProcessExecutor executor)
            throws Exception {
        // Create a ProcessExecutorResult instance through reflection to avoid direct reference
        // to the inner class in our code
        Class<?> resultClass =
                Class.forName("stirling.software.SPDF.utils.ProcessExecutor$ProcessExecutorResult");

        // Create instance through the owning ProcessExecutor instance
        java.lang.reflect.Constructor<?> constructor =
                resultClass.getDeclaredConstructor(ProcessExecutor.class, int.class, String.class);
        constructor.setAccessible(true);
        Object result = constructor.newInstance(executor, 0, "Success");

        return (ProcessExecutor.ProcessExecutorResult) result;
    }
}
