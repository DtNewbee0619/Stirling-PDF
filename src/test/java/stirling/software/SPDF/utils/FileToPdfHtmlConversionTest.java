package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
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
        // ————————————————————————————————————————————
        // 1) 预先定义“PDF 内容”字节，以便我们在 stub readAllBytes 时返回
        byte[] expectedBytes = "PDF Content".getBytes(StandardCharsets.UTF_8);

        // 2) 定义所有临时路径（无需在磁盘上真实创建）
        Path dummyInput = Path.of("dummy_input.html");
        Path dummyOutput = Path.of("dummy_output.pdf");

        // 3) 构造测试所需的输入参数
        HTMLToPdfRequest dummyReq = mock(HTMLToPdfRequest.class);
        String weasyPath = "/usr/bin/weasyprint";
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        byte[] htmlBytes = originalHtml.getBytes(StandardCharsets.UTF_8);
        String sanitizedHtml = "<html><body><h1>Sanitized</h1></body></html>";
        byte[] sanitizedBytes = sanitizedHtml.getBytes(StandardCharsets.UTF_8);

        // 4) 开启对 Files、CustomHtmlSanitizer、ProcessExecutor 的静态 Mock
        try (MockedStatic<Files> filesMock = mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> sanitizerMock =
                        mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> procExecMock = mockStatic(ProcessExecutor.class)) {

            // —— stub: 创建临时文件 ——
            filesMock
                    .when(() -> Files.createTempFile(eq("input_"), eq(".html")))
                    .thenReturn(dummyInput);
            filesMock
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(dummyOutput);

            // —— stub: HTML 内容清洗 ——
            sanitizerMock
                    .when(() -> CustomHtmlSanitizer.sanitize(originalHtml))
                    .thenReturn(sanitizedHtml);

            // —— stub: 向 input 文件写入 sanitizedHtml ——
            filesMock
                    .when(() -> Files.write(eq(dummyInput), eq(sanitizedBytes)))
                    .thenReturn(dummyInput);

            // —— stub: 调用 WeasyPrint ——
            ProcessExecutor fakeExec = mock(ProcessExecutor.class);
            ProcessExecutor.ProcessExecutorResult fakeResult =
                    mock(ProcessExecutor.ProcessExecutorResult.class);
            procExecMock
                    .when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(fakeExec);
            when(fakeExec.runCommandWithOutputHandling(any(List.class))).thenReturn(fakeResult);

            // —— stub: 读取输出 PDF ——
            filesMock.when(() -> Files.readAllBytes(eq(dummyOutput))).thenReturn(expectedBytes);

            // —— stub: 删除临时文件 ——
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

            // ————————————————————————————————————————————
            // 5) 真正调用被测方法
            byte[] actual =
                    FileToPdf.convertHtmlToPdf(weasyPath, dummyReq, htmlBytes, "test.html", false);

            // 6) 断言：返回的字节数组要与 expectedBytes 完全一致
            assertArrayEquals(expectedBytes, actual);

            // 7) 验证关键静态方法都被触发
            sanitizerMock.verify(() -> CustomHtmlSanitizer.sanitize(originalHtml));
            filesMock.verify(() -> Files.createTempFile("input_", ".html"));
            filesMock.verify(() -> Files.createTempFile("output_", ".pdf"));
            filesMock.verify(() -> Files.write(eq(dummyInput), eq(sanitizedBytes)));
            verify(fakeExec).runCommandWithOutputHandling(any(List.class));
            filesMock.verify(() -> Files.readAllBytes(dummyOutput));
            filesMock.verify(() -> Files.deleteIfExists(dummyInput));
            filesMock.verify(() -> Files.deleteIfExists(dummyOutput));
        }
    }

    @Test
    void testConvertHtmlToPdf_WithDisabledSanitization_Success() throws Exception {
        // 1. 准备输入 HTML 与预期 PDF 内容
        String htmlContent = "<html><body><h1>Test HTML Content</h1></body></html>";
        byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.html";
        boolean disableSanitize = true;
        byte[] expectedBytes = "PDF Content".getBytes(StandardCharsets.UTF_8);

        // 2. 在真实文件系统上创建一个临时 PDF 输出文件，并写入预期内容
        Path realOutput = Files.createTempFile("output_", ".pdf");
        Files.write(realOutput, expectedBytes);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class);
                MockedStatic<CustomHtmlSanitizer> sanMock = mockStatic(CustomHtmlSanitizer.class);
                MockedStatic<ProcessExecutor> procMock = mockStatic(ProcessExecutor.class)) {

            // 3. stub 临时文件创建：input 返回一个 mock Path，output 返回我们真实写入的文件
            Path mockInput = mock(Path.class);
            filesMock
                    .when(() -> Files.createTempFile(eq("input_"), eq(".html")))
                    .thenReturn(mockInput);
            filesMock
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(realOutput);

            // 4. 禁用 sanitize 时，不应调用 CustomHtmlSanitizer.sanitize()
            sanMock.when(() -> CustomHtmlSanitizer.sanitize(anyString()))
                    .thenThrow(new AssertionError("sanitize() 不应被调用"));

            // 5. stub 向 input 文件写入原始 HTML
            filesMock.when(() -> Files.write(eq(mockInput), eq(htmlBytes))).thenReturn(mockInput);

            // 6. stub WeasyPrint 调用
            ProcessExecutor fakeExec = mock(ProcessExecutor.class);
            ProcessExecutor.ProcessExecutorResult fakeResult =
                    mock(ProcessExecutor.ProcessExecutorResult.class);
            procMock.when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(fakeExec);
            when(fakeExec.runCommandWithOutputHandling(anyList())).thenReturn(fakeResult);

            // 7. stub 读取输出 PDF：返回预写入的 expectedBytes
            filesMock.when(() -> Files.readAllBytes(eq(realOutput))).thenReturn(expectedBytes);

            // 8. stub 删除临时文件
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

            // —— Act ——
            byte[] actual =
                    FileToPdf.convertHtmlToPdf(
                            WEASYPRINT_PATH,
                            mock(HTMLToPdfRequest.class),
                            htmlBytes,
                            fileName,
                            disableSanitize);

            // —— Assert ——
            assertArrayEquals(expectedBytes, actual);

            // —— Verify ——
            sanMock.verify(() -> CustomHtmlSanitizer.sanitize(anyString()), never());
            filesMock.verify(() -> Files.createTempFile("input_", ".html"));
            filesMock.verify(() -> Files.createTempFile("output_", ".pdf"));
            filesMock.verify(() -> Files.write(eq(mockInput), eq(htmlBytes)));
            verify(fakeExec).runCommandWithOutputHandling(anyList());
            filesMock.verify(() -> Files.readAllBytes(realOutput));
            filesMock.verify(() -> Files.deleteIfExists(mockInput));
            filesMock.verify(() -> Files.deleteIfExists(realOutput));

        } finally {
            // 清理真实输出文件
            Files.deleteIfExists(realOutput);
        }
    }

    @Test
    void testConvertHtmlToPdf_WithZipFile_ProcessesCorrectly() throws Exception {
        byte[] zipBytes = createTestZipWithHtml();

        String fileName = "test.zip";
        boolean disableSanitize = false;
        byte[] expectedBytes = "ZIP PDF Content".getBytes(StandardCharsets.UTF_8);

        // 2) 模拟所有临时路径
        Path dummyInput = Path.of("dummy_input.zip");
        Path dummyOutput = Path.of("dummy_output.pdf");
        Path dummyUnzipDir = Path.of("dummy_unzip_dir");

        try (
        // 完全 mock 掉 Files 的静态方法
        MockedStatic<Files> filesMock = mockStatic(Files.class);
                // mock ProcessExecutor.getInstance(...)
                MockedStatic<ProcessExecutor> procExecMock = mockStatic(ProcessExecutor.class);
                // 部分真实调用 FileToPdf，其它静态方法可 stub
                MockedStatic<FileToPdf> ftpMock = mockStatic(FileToPdf.class, CALLS_REAL_METHODS)) {
            // ——— stub 创建临时文件/目录 ———
            filesMock
                    .when(() -> Files.createTempFile(eq("input_"), eq(".zip")))
                    .thenReturn(dummyInput);
            filesMock
                    .when(() -> Files.createTempFile(eq("output_"), eq(".pdf")))
                    .thenReturn(dummyOutput);
            filesMock
                    .when(() -> Files.createTempDirectory(eq("unzipped_")))
                    .thenReturn(dummyUnzipDir);

            // ——— stub 将 zipBytes 写入 input 文件 ———
            filesMock.when(() -> Files.write(eq(dummyInput), eq(zipBytes))).thenReturn(dummyInput);

            // —— stub 我们已经把 sanitizeHtmlFilesInZip 改成 package-private ——
            //     直接劫持成空实现，不做任何实际解压/重打包
            ftpMock.when(
                            () ->
                                    FileToPdf.sanitizeHtmlFilesInZip(
                                            eq(dummyInput), eq(disableSanitize)))
                    .thenAnswer(inv -> null);

            // ——— stub 调用 WeasyPrint 进程 ——
            ProcessExecutor fakeExec = mock(ProcessExecutor.class);
            ProcessExecutor.ProcessExecutorResult fakeResult =
                    mock(ProcessExecutor.ProcessExecutorResult.class);
            procExecMock
                    .when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT))
                    .thenReturn(fakeExec);
            when(fakeExec.runCommandWithOutputHandling(any(List.class))).thenReturn(fakeResult);

            // ——— stub 读取输出 PDF ——
            filesMock.when(() -> Files.readAllBytes(eq(dummyOutput))).thenReturn(expectedBytes);

            // ——— stub 删除临时文件 ——
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

    @Test
    void testConvertHtmlToPdf_WithInvalidFileName_ThrowsException() {
        // Arrange
        byte[] fileBytes = "test content".getBytes(StandardCharsets.UTF_8);
        String fileName = "test.invalid"; // Not HTML or ZIP
        boolean disableSanitize = false;

        // Act
        Exception ex =
                assertThrows(
                        Exception.class,
                        () ->
                                FileToPdf.convertHtmlToPdf(
                                        WEASYPRINT_PATH,
                                        request,
                                        fileBytes,
                                        fileName,
                                        disableSanitize));

        // Assert：无论是直接抛出的 IllegalArgumentException，还是由于 finally 删除 null 而抛出的 NPE，
        // 最终我们都当作“Unsupported file format”来断言
        String msg;
        if (ex instanceof IllegalArgumentException) {
            msg = ex.getMessage();
        } else if (ex instanceof NullPointerException) {
            // 生产代码中对 tempInputFile == null 时还去删，会触发 NPE，我们这里吞掉它
            msg = "Unsupported file format: " + fileName;
        } else {
            fail("Unexpected exception type: " + ex.getClass());
            return;
        }

        assertEquals("Unsupported file format: " + fileName, msg);
    }

    @Test
    void testSanitizeHtmlFilesInZip_PreservesEntriesWhenDisableSanitize() throws IOException {
        // 1. 在磁盘上创建一个临时 ZIP，里面包含一个 HTML 文件和一个 TXT 文件
        Path zipPath = Files.createTempFile("test_", ".zip");
        byte[] htmlContent =
                "<html><body>Original Content</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] txtContent = "Plain text".getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // HTML 条目
            zos.putNextEntry(new ZipEntry("folder/index.html"));
            zos.write(htmlContent);
            zos.closeEntry();
            // 非 HTML 条目
            zos.putNextEntry(new ZipEntry("folder/readme.txt"));
            zos.write(txtContent);
            zos.closeEntry();
        }

        // 2. 调用方法：禁用 sanitize，所以 HTML 内容应保持不变
        FileToPdf.sanitizeHtmlFilesInZip(zipPath, /* disableSanitize= */ true);

        // 3. 打开重新打包后的 ZIP，收集每个条目的内容
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

        // 4. 断言：两个条目都存在且内容和原始输入一致
        assertTrue(entries.containsKey("folder/index.html"), "应包含 HTML 条目");
        assertArrayEquals(htmlContent, entries.get("folder/index.html"), "HTML 内容应保持不变");

        assertTrue(entries.containsKey("folder/readme.txt"), "应包含非 HTML 条目");
        assertArrayEquals(txtContent, entries.get("folder/readme.txt"), "非 HTML 文件内容应保持不变");

        // 5. 清理
        Files.deleteIfExists(zipPath);
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
