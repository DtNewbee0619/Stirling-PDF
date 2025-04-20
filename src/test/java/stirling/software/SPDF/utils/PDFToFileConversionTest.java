package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.utils.ProcessExecutor.ProcessExecutorResult;

@ExtendWith(MockitoExtension.class)
class PDFToFileConversionTest {
    private final String PDFTOHTML = "pdftohtml";
    private final String PDFTOHTML_SINGLE = "pdftohtml"; // same exec
    private final String SOFFICE = "soffice";

    //
    // processPdfToMarkdown
    //

    @Test
    void testProcessPdfToMarkdown_InvalidContentType() throws Exception {
        MultipartFile bad = new MockMultipartFile("file", "foo.txt", "text/plain", new byte[0]);
        ResponseEntity<byte[]> resp = new PDFToFile().processPdfToMarkdown(bad);
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void testProcessPdfToMarkdown_SingleMarkdownFile() throws Exception {
        // ========== 准备真实输出目录 ==========
        // 1. temp input PDF（不关心内容）
        Path realInput = Files.createTempFile("input_real", ".pdf");
        Files.write(realInput, "dummy".getBytes());
        // 2. temp output dir，里面只放一个 .html 文件
        Path realOutDir = Files.createTempDirectory("output_real_");
        Path html = realOutDir.resolve("page.html");
        String htmlContent = "<h1>Hello</h1>";
        Files.writeString(html, htmlContent);

        // ========== 构造 MultipartFile ==========
        MultipartFile mf = mock(MultipartFile.class);
        when(mf.getContentType()).thenReturn("application/pdf");
        when(mf.getOriginalFilename()).thenReturn("report.pdf");
        doNothing().when(mf).transferTo(realInput);

        // ========== Mock 静态 ==========
        byte[] expectedMarkdown =
                ("# Hello" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        ResponseEntity<byte[]> fakeResponse =
                ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"report.md\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(expectedMarkdown);

        try (MockedStatic<Files> fs = mockStatic(Files.class);
                MockedStatic<ProcessExecutor> ps = mockStatic(ProcessExecutor.class);
                MockedStatic<FileUtils> fu = mockStatic(FileUtils.class);
                MockedStatic<WebResponseUtils> wr = mockStatic(WebResponseUtils.class)) {

            // stub createTempFile/createTempDirectory
            fs.when(() -> Files.createTempFile(eq("input_"), eq(".pdf"))).thenReturn(realInput);
            fs.when(() -> Files.createTempDirectory(eq("output_"))).thenReturn(realOutDir);

            // stub pdftohtml 调用
            ProcessExecutor exec = mock(ProcessExecutor.class);
            ProcessExecutorResult result = mock(ProcessExecutorResult.class);
            ps.when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.PDFTOHTML))
                    .thenReturn(exec);
            when(exec.runCommandWithOutputHandling(anyList(), any(File.class))).thenReturn(result);

            // stub zip 清理
            fu.when(() -> FileUtils.deleteDirectory(any(File.class))).thenReturn(null);

            // stub 最终响应
            wr.when(
                            () ->
                                    WebResponseUtils.bytesToWebResponse(
                                            eq(expectedMarkdown),
                                            eq("report.md"),
                                            eq(MediaType.APPLICATION_OCTET_STREAM)))
                    .thenReturn(fakeResponse);

            // ========== 执行并断言 ==========
            PDFToFile svc = new PDFToFile();
            ResponseEntity<byte[]> resp = svc.processPdfToMarkdown(mf);

            assertEquals(200, resp.getStatusCodeValue());
            assertArrayEquals(expectedMarkdown, resp.getBody());

            // 验证调用
            fs.verify(() -> Files.createTempFile("input_", ".pdf"));
            fs.verify(() -> Files.createTempDirectory("output_"));
            ps.verify(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.PDFTOHTML));
            wr.verify(
                    () ->
                            WebResponseUtils.bytesToWebResponse(
                                    expectedMarkdown,
                                    "report.md",
                                    MediaType.APPLICATION_OCTET_STREAM));
        } finally {
            Files.deleteIfExists(realInput);
            FileUtils.deleteDirectory(realOutDir.toFile());
        }
    }

    //
    // processPdfToHtml
    //

    @Test
    void testProcessPdfToHtml_InvalidContentType() throws Exception {
        MultipartFile bad = new MockMultipartFile("f", "a.pdf", "application/json", new byte[0]);
        ResponseEntity<byte[]> resp = new PDFToFile().processPdfToHtml(bad);
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void testProcessPdfToHtml_ZipAllFiles() throws Exception {
        // 1. 准备真实输出目录，放两个文件
        Path realIn = Files.createTempFile("in_real", ".pdf");
        Files.write(realIn, new byte[] {0});
        Path realOutDir = Files.createTempDirectory("out_real_");
        Files.writeString(realOutDir.resolve("a.html"), "<p>A</p>");
        Files.writeString(realOutDir.resolve("img.png"), "PNG");

        // MultipartFile stub
        MultipartFile mf = mock(MultipartFile.class);
        when(mf.getContentType()).thenReturn("application/pdf");
        when(mf.getOriginalFilename()).thenReturn("x.pdf");
        doNothing().when(mf).transferTo(realIn);

        // stub pdftohtml
        ProcessExecutor exec = mock(ProcessExecutor.class);
        ProcessExecutorResult res = mock(ProcessExecutorResult.class);

        // collect ZIP bytes
        byte[] zipBytes = Files.readAllBytes(realOutDir.resolve("a.html")); // dummy

        ResponseEntity<byte[]> fakeResp = ResponseEntity.ok().body(zipBytes);

        try (MockedStatic<Files> fs = mockStatic(Files.class);
                MockedStatic<ProcessExecutor> ps = mockStatic(ProcessExecutor.class);
                MockedStatic<FileUtils> fu = mockStatic(FileUtils.class);
                MockedStatic<WebResponseUtils> wr = mockStatic(WebResponseUtils.class)) {

            fs.when(() -> Files.createTempFile(eq("input_"), eq(".pdf"))).thenReturn(realIn);
            fs.when(() -> Files.createTempDirectory(eq("output_"))).thenReturn(realOutDir);

            ps.when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.PDFTOHTML))
                    .thenReturn(exec);
            when(exec.runCommandWithOutputHandling(anyList(), any(File.class))).thenReturn(res);

            fu.when(() -> FileUtils.deleteDirectory(any(File.class))).thenReturn(null);

            wr.when(
                            () ->
                                    WebResponseUtils.bytesToWebResponse(
                                            any(byte[].class),
                                            eq("xToHtml.zip"),
                                            eq(MediaType.APPLICATION_OCTET_STREAM)))
                    .thenReturn(fakeResp);

            PDFToFile svc = new PDFToFile();
            ResponseEntity<byte[]> resp = svc.processPdfToHtml(mf);

            assertSame(fakeResp, resp);
            ps.verify(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.PDFTOHTML));
            wr.verify(
                    () ->
                            WebResponseUtils.bytesToWebResponse(
                                    any(byte[].class),
                                    eq("xToHtml.zip"),
                                    eq(MediaType.APPLICATION_OCTET_STREAM)));
        } finally {
            Files.deleteIfExists(realIn);
            FileUtils.deleteDirectory(realOutDir.toFile());
        }
    }

    //
    // processPdfToOfficeFormat
    //

    @Test
    void testProcessPdfToOfficeFormat_InvalidContentType() throws Exception {
        MultipartFile bad = new MockMultipartFile("f", "foo.pdf", "text/plain", new byte[0]);
        ResponseEntity<byte[]> resp =
                new PDFToFile().processPdfToOfficeFormat(bad, "docx", "writer_pdf_Export");
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void testProcessPdfToOfficeFormat_InvalidFormat() throws Exception {
        MultipartFile mf = new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[0]);
        ResponseEntity<byte[]> resp = new PDFToFile().processPdfToOfficeFormat(mf, "exe", "");
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void testProcessPdfToOfficeFormat_SingleOutput() throws Exception {
        Path realIn = Files.createTempFile("in_real", ".pdf");
        Files.write(realIn, new byte[] {0});
        Path realOutDir = Files.createTempDirectory("out_real_");
        // simulate LibreOffice 输出一个 docx
        File outFile = realOutDir.resolve("a.docx").toFile();
        try (var fos = Files.newOutputStream(outFile.toPath())) {
            fos.write(new byte[] {1, 2, 3});
        }

        MultipartFile mf = mock(MultipartFile.class);
        when(mf.getContentType()).thenReturn("application/pdf");
        when(mf.getOriginalFilename()).thenReturn("foo.pdf");
        doNothing().when(mf).transferTo(realIn);

        ProcessExecutor exec = mock(ProcessExecutor.class);
        ProcessExecutorResult res = mock(ProcessExecutorResult.class);

        byte[] fileBytes = FileUtils.readFileToByteArray(outFile);
        ResponseEntity<byte[]> fakeResp = ResponseEntity.ok().body(fileBytes);

        try (MockedStatic<Files> fs = mockStatic(Files.class);
                MockedStatic<ProcessExecutor> ps = mockStatic(ProcessExecutor.class);
                MockedStatic<FileUtils> fu = mockStatic(FileUtils.class);
                MockedStatic<WebResponseUtils> wr = mockStatic(WebResponseUtils.class)) {

            fs.when(() -> Files.createTempFile(eq("input_"), eq(".pdf"))).thenReturn(realIn);
            fs.when(() -> Files.createTempDirectory(eq("output_"))).thenReturn(realOutDir);

            ps.when(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.LIBRE_OFFICE))
                    .thenReturn(exec);
            when(exec.runCommandWithOutputHandling(anyList())).thenReturn(res);

            // stub FileUtils.readFileToByteArray & deleteDirectory
            fu.when(() -> FileUtils.readFileToByteArray(outFile)).thenReturn(fileBytes);
            fu.when(() -> FileUtils.deleteDirectory(realOutDir.toFile())).thenReturn(null);

            wr.when(
                            () ->
                                    WebResponseUtils.bytesToWebResponse(
                                            eq(fileBytes),
                                            eq("foo.docx"),
                                            eq(MediaType.APPLICATION_OCTET_STREAM)))
                    .thenReturn(fakeResp);

            PDFToFile svc = new PDFToFile();
            ResponseEntity<byte[]> resp =
                    svc.processPdfToOfficeFormat(mf, "docx", "writer_pdf_Export");

            assertSame(fakeResp, resp);
            ps.verify(() -> ProcessExecutor.getInstance(ProcessExecutor.Processes.LIBRE_OFFICE));
            wr.verify(
                    () ->
                            WebResponseUtils.bytesToWebResponse(
                                    fileBytes, "foo.docx", MediaType.APPLICATION_OCTET_STREAM));
        } finally {
            Files.deleteIfExists(realIn);
            FileUtils.deleteDirectory(realOutDir.toFile());
        }
    }
}
