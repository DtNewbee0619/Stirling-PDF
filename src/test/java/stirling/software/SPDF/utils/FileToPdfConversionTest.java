package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.SPDF.model.api.converters.HTMLToPdfRequest;
import stirling.software.SPDF.utils.ProcessExecutor.ProcessExecutorResult;

@ExtendWith(MockitoExtension.class)
public class FileToPdfConversionTest {

    @Mock private ProcessExecutor mockProcessExecutor;

    @Mock private ProcessExecutorResult mockProcessExecutorResult;

    private HTMLToPdfRequest request;
    private final String weasyprintPath = "/usr/bin/weasyprint";

    // Access private methods via reflection
    private Method sanitizeHtmlContentMethod;
    private Method sanitizeHtmlFilesInZipMethod;

    @BeforeEach
    public void setup() throws Exception {
        request = new HTMLToPdfRequest();
        request.setZoom(1.0f);

        // Initialize reflection methods
        sanitizeHtmlContentMethod =
                FileToPdf.class.getDeclaredMethod(
                        "sanitizeHtmlContent", String.class, boolean.class);
        sanitizeHtmlContentMethod.setAccessible(true);

        sanitizeHtmlFilesInZipMethod =
                FileToPdf.class.getDeclaredMethod(
                        "sanitizeHtmlFilesInZip", Path.class, boolean.class);
        sanitizeHtmlFilesInZipMethod.setAccessible(true);
    }

    /*
     * Test cases for convertHtmlToPdf method
     */
    @Test
    public void testConvertHtmlToPdf_WithValidHtml() throws IOException, InterruptedException {
        // Arrange
        String htmlContent = "<html><body><h1>Test</h1></body></html>";
        byte[] fileBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = false;
        byte[] expectedPdfBytes = "PDF content".getBytes();

        // Create temporary paths to mock
        Path mockTempOutputFile = mock(Path.class);
        Path mockTempInputFile = mock(Path.class);

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        Mockito.mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        Mockito.mockStatic(ProcessExecutor.class)) {

            // Mock Files utility methods
            when(Files.createTempFile(eq("output_"), eq(".pdf"))).thenReturn(mockTempOutputFile);
            when(Files.createTempFile(eq("input_"), eq(".html"))).thenReturn(mockTempInputFile);
            when(Files.readAllBytes(mockTempOutputFile)).thenReturn(expectedPdfBytes);

            // Mock sanitizer
            when(CustomHtmlSanitizer.sanitize(anyString())).thenReturn(htmlContent);

            // Mock ProcessExecutor
            when(ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(mockProcessExecutor);
            when(mockProcessExecutor.runCommandWithOutputHandling(anyList()))
                    .thenReturn(mockProcessExecutorResult);

            mockedFiles
                    .when(() -> Files.write(eq(mockTempInputFile), any(byte[].class)))
                    .thenReturn(mockTempInputFile);

            // Act
            byte[] result =
                    FileToPdf.convertHtmlToPdf(
                            weasyprintPath, request, fileBytes, fileName, disableSanitize);

            // Assert
            assertNotNull(result);
            assertEquals(expectedPdfBytes.length, result.length);

            // Verify interactions
            verify(mockProcessExecutor).runCommandWithOutputHandling(anyList());
            mockedFiles.verify(() -> Files.write(eq(mockTempInputFile), any(byte[].class)));
            mockedFiles.verify(() -> Files.readAllBytes(mockTempOutputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempOutputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempInputFile));
        }
    }

    /*
     * Test case: Invalid file format
     *
     * <p>This test verifies that an exception is thrown when the input file is not in HTML or ZIP
     * format.
     */
    @Test
    public void testConvertHtmlToPdf_WithUnsupportedFormat() throws IOException {
        // Arrange
        byte[] fileBytes = "content".getBytes();
        String fileName = "test.txt"; // Not an .html or .zip file
        boolean disableSanitize = false;

        // Need to mock Files class to prevent NullPointerException
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            // Mock Files.createTempFile and Files.deleteIfExists to avoid NPE
            Path mockTempOutputFile = mock(Path.class);
            when(Files.createTempFile(anyString(), anyString())).thenReturn(mockTempOutputFile);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(false);

            // Act & Assert
            IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            weasyprintPath,
                                            request,
                                            fileBytes,
                                            fileName,
                                            disableSanitize));

            assertTrue(exception.getMessage().contains("Unsupported file format"));

            // Verify cleanup was attempted
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempOutputFile));
        }
    }

    /*
     * Test case: ProcessExecutor throws IOException
     *
     * <p>This test verifies that the method handles IOException thrown by ProcessExecutor.
     */
    @Test
    public void testConvertHtmlToPdf_ProcessException() throws IOException, InterruptedException {
        // Arrange
        String htmlContent = "<html><body><h1>Test</h1></body></html>";
        byte[] fileBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = false;
        IOException expectedException = new IOException("Process error");

        // Create temporary paths to mock
        Path mockTempOutputFile = mock(Path.class);
        Path mockTempInputFile = mock(Path.class);

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        Mockito.mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        Mockito.mockStatic(ProcessExecutor.class)) {

            // Mock Files utility methods
            when(Files.createTempFile(eq("output_"), eq(".pdf"))).thenReturn(mockTempOutputFile);
            when(Files.createTempFile(eq("input_"), eq(".html"))).thenReturn(mockTempInputFile);
            when(Files.readAllBytes(mockTempOutputFile)).thenReturn(new byte[0]); // Empty PDF

            // Mock sanitizer
            when(CustomHtmlSanitizer.sanitize(anyString())).thenReturn(htmlContent);

            // Mock ProcessExecutor to throw exception
            when(ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(mockProcessExecutor);
            when(mockProcessExecutor.runCommandWithOutputHandling(anyList()))
                    .thenThrow(expectedException);

            mockedFiles
                    .when(() -> Files.write(eq(mockTempInputFile), any(byte[].class)))
                    .thenReturn(mockTempInputFile);

            // Act & Assert
            IOException exception =
                    assertThrows(
                            IOException.class,
                            () ->
                                    FileToPdf.convertHtmlToPdf(
                                            weasyprintPath,
                                            request,
                                            fileBytes,
                                            fileName,
                                            disableSanitize));

            assertEquals(expectedException, exception);

            // Verify cleanup was called even after exception
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempOutputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempInputFile));
        }
    }

    /*
     * Test case: sanitizeZipFilename method
     *
     * <p>This test verifies that the method correctly sanitizes the filename for ZIP files.
     */
    @Test
    public void testSanitizeZipFilename() {
        // Test null or empty input
        assertEquals("", FileToPdf.sanitizeZipFilename(null));
        assertEquals("", FileToPdf.sanitizeZipFilename(""));
        assertEquals("", FileToPdf.sanitizeZipFilename("  "));

        // Test path traversal removal
        assertEquals("file.txt", FileToPdf.sanitizeZipFilename("../file.txt"));
        assertEquals("file.txt", FileToPdf.sanitizeZipFilename("..\\file.txt"));
        assertEquals(
                "some/path/to/file.txt",
                FileToPdf.sanitizeZipFilename("../some/../path/..\\to\\file.txt"));

        // Test drive letter removal
        assertEquals("folder/file.txt", FileToPdf.sanitizeZipFilename("C:\\folder\\file.txt"));
        assertEquals("folder/file.txt", FileToPdf.sanitizeZipFilename("D:/folder/file.txt"));

        // Test leading slash removal
        assertEquals("folder/file.txt", FileToPdf.sanitizeZipFilename("/folder/file.txt"));
        assertEquals("folder/file.txt", FileToPdf.sanitizeZipFilename("\\folder\\file.txt"));

        // Test safe path handling
        assertEquals("safe/path/file.txt", FileToPdf.sanitizeZipFilename("safe/path/file.txt"));
    }

    /*
     * Test case: sanitizeHtmlContent method
     *
     * <p>This test verifies that the method correctly sanitizes HTML content.
     */
    @Test
    public void testSanitizeHtmlContent() throws Exception {
        // Set up some test HTML
        String unsafeHtml = "<html><body><script>alert('xss')</script><h1>Title</h1></body></html>";
        String safeHtml = "<html><body><h1>Title</h1></body></html>";

        try (MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                Mockito.mockStatic(CustomHtmlSanitizer.class)) {
            // Mock the sanitizer response
            when(CustomHtmlSanitizer.sanitize(unsafeHtml)).thenReturn(safeHtml);

            // Test with sanitization enabled
            String result1 = (String) sanitizeHtmlContentMethod.invoke(null, unsafeHtml, false);
            assertEquals(safeHtml, result1);

            // Test with sanitization disabled
            String result2 = (String) sanitizeHtmlContentMethod.invoke(null, unsafeHtml, true);
            assertEquals(unsafeHtml, result2);

            // Verify interactions
            mockedSanitizer.verify(() -> CustomHtmlSanitizer.sanitize(unsafeHtml), times(1));
        }
    }

    /*
     * Test case: sanitizeHtmlFilesInZip method
     *
     * <p>This test verifies that the method correctly sanitizes HTML files in a ZIP archive.
     */
    @Test
    public void testConvertHtmlToPdf_ErrorHandling() throws IOException, InterruptedException {
        // Arrange
        String htmlContent = "<html><body><h1>Test</h1></body></html>";
        byte[] fileBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = false;
        byte[] pdfBytes = "PDF content".getBytes();

        // Create temporary paths to mock
        Path mockTempOutputFile = mock(Path.class);
        Path mockTempInputFile = mock(Path.class);

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> mockedSanitizer =
                        Mockito.mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> mockedProcessExecutor =
                        Mockito.mockStatic(ProcessExecutor.class)) {

            // Mock Files utility methods
            when(Files.createTempFile(eq("output_"), eq(".pdf"))).thenReturn(mockTempOutputFile);
            when(Files.createTempFile(eq("input_"), eq(".html"))).thenReturn(mockTempInputFile);

            // Set up a case where the first readAllBytes throws IOException
            // but the second readAllBytes in the catch block returns valid bytes
            when(Files.readAllBytes(mockTempOutputFile))
                    .thenThrow(new IOException("First read error"))
                    .thenReturn(pdfBytes);

            // Mock sanitizer
            when(CustomHtmlSanitizer.sanitize(anyString())).thenReturn(htmlContent);

            // Mock ProcessExecutor
            when(ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(mockProcessExecutor);
            when(mockProcessExecutor.runCommandWithOutputHandling(anyList()))
                    .thenReturn(mockProcessExecutorResult);

            mockedFiles
                    .when(() -> Files.write(eq(mockTempInputFile), any(byte[].class)))
                    .thenReturn(mockTempInputFile);

            // Act
            byte[] result =
                    FileToPdf.convertHtmlToPdf(
                            weasyprintPath, request, fileBytes, fileName, disableSanitize);

            // Assert - should recover from first error and return the PDF bytes from second attempt
            assertNotNull(result);
            assertEquals(pdfBytes.length, result.length);

            // Verify interactions
            verify(mockProcessExecutor).runCommandWithOutputHandling(anyList());
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempOutputFile));
            mockedFiles.verify(() -> Files.deleteIfExists(mockTempInputFile));
        }
    }

    /*
     * Test case: convertHtmlToPdf with ZIP file
     *
     * <p>This test verifies that the method correctly processes a ZIP file containing HTML files.
     */
    @Test
    void testConvertHtmlToPdf_WithZipFile_ProcessesCorrectly() throws Exception {
        byte[] zipBytes = createTestZipWithHtml();

        String fileName = "test.zip";
        boolean disableSanitize = false;
        byte[] expectedBytes = "ZIP PDF Content".getBytes(StandardCharsets.UTF_8);

        Path dummyInput = Path.of("dummy_input.zip");
        Path dummyOutput = Path.of("dummy_output.pdf");
        Path dummyUnzipDir = Path.of("dummy_unzip_dir");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class);
                MockedStatic<ProcessExecutor> procExecMock = mockStatic(ProcessExecutor.class);
                MockedStatic<FileToPdf> ftpMock = mockStatic(FileToPdf.class, CALLS_REAL_METHODS)) {

            filesMock
                    .when(() -> Files.createTempFile(eq("input_"), eq(".zip")))
                    .thenReturn(dummyInput);
            filesMock
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(dummyOutput);
            filesMock
                    .when(() -> Files.createTempDirectory(eq("unzipped_")))
                    .thenReturn(dummyUnzipDir);

            filesMock.when(() -> Files.write(eq(dummyInput), eq(zipBytes))).thenReturn(dummyInput);

            ftpMock.when(
                            () ->
                                    FileToPdf.sanitizeHtmlFilesInZip(
                                            eq(dummyInput), eq(disableSanitize)))
                    .thenAnswer(inv -> null);

            ProcessExecutor fakeExec = mock(ProcessExecutor.class);
            ProcessExecutor.ProcessExecutorResult fakeResult =
                    mock(ProcessExecutor.ProcessExecutorResult.class);
            procExecMock
                    .when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(fakeExec);
            when(fakeExec.runCommandWithOutputHandling(any(List.class))).thenReturn(fakeResult);

            filesMock.when(() -> Files.readAllBytes(eq(dummyOutput))).thenReturn(expectedBytes);

            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

            // ——— Act ———
            byte[] actual =
                    FileToPdf.convertHtmlToPdf(
                            "/usr/bin/weasyprint",
                            mock(HTMLToPdfRequest.class),
                            zipBytes,
                            fileName,
                            disableSanitize);

            // ——— Assert ———
            assertArrayEquals(expectedBytes, actual);

            // ——— Verify ———
            filesMock.verify(() -> Files.createTempFile("input_", ".zip"));
            filesMock.verify(() -> Files.createTempFile("output_", ".pdf"));
            filesMock.verify(() -> Files.createTempDirectory("unzipped_"));
            filesMock.verify(() -> Files.write(dummyInput, zipBytes));
            ftpMock.verify(() -> FileToPdf.sanitizeHtmlFilesInZip(dummyInput, disableSanitize));
            verify(fakeExec).runCommandWithOutputHandling(any(List.class));
            filesMock.verify(() -> Files.readAllBytes(dummyOutput));
            filesMock.verify(() -> Files.deleteIfExists(dummyInput));
            filesMock.verify(() -> Files.deleteIfExists(dummyOutput));
        }
    }

    /*
     * Helper method to create a test ZIP file with HTML and CSS files
     */
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

    /*
     * Test case: sanitizeHtmlFilesInZip method
     *
     * <p>This test verifies that the method correctly sanitizes HTML files in a ZIP archive.
     */
    @Test
    void testSanitizeHtmlFilesInZip_PreservesEntriesWhenDisableSanitize() throws IOException {
        Path zipPath = Files.createTempFile("test_", ".zip");
        byte[] htmlContent =
                "<html><body>Original Content</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] txtContent = "Plain text".getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("folder/index.html"));
            zos.write(htmlContent);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("folder/readme.txt"));
            zos.write(txtContent);
            zos.closeEntry();
        }

        FileToPdf.sanitizeHtmlFilesInZip(zipPath, /* disableSanitize= */ true);

        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                entries.put(entry.getName(), baos.toByteArray());
                zis.closeEntry();
            }
        }

        assertTrue(entries.containsKey("folder/index.html"), "Should include HTML entries");
        assertArrayEquals(
                htmlContent,
                entries.get("folder/index.html"),
                "HTML content should remain unchanged");

        assertTrue(entries.containsKey("folder/readme.txt"), "Should include non-HTML entries");
        assertArrayEquals(
                txtContent,
                entries.get("folder/readme.txt"),
                "Non-HTML file content should remain unchanged");

        Files.deleteIfExists(zipPath);
    }
}
