package stirling.software.SPDF.controller.api.converters;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.enabled=false",
            "security.enableLogin=false",
            "security.csrfDisabled=true",
            "system.enableUrlToPDF=false",
            "system.enableAlphaFunctionality=false",
            "system.disableSanitize=false"
        })
@AutoConfigureMockMvc(addFilters = false)
public class HtmlToPdfIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static boolean weasyprintAvailable = false;

    @BeforeAll
    static void checkWeasyprint() {
        // Check for weasyprint once before all tests
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "weasyprint");
            Process process = pb.start();
            weasyprintAvailable = (process.waitFor() == 0);
            if (!weasyprintAvailable) {
                System.out.println(
                        "Skipping WeasyPrint dependent tests: weasyprint is not installed in this environment");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to check for weasyprint: " + e.getMessage());
            weasyprintAvailable = false;
        }
    }

    // Helper method to skip tests if weasyprint is not available
    private void assumeWeasyprint() {
        Assumptions.assumeTrue(
                weasyprintAvailable, "Skipping test: weasyprint is not available/found.");
    }

    @Test
    public void convertValidHtmlToPdf_shouldReturnPdfBytes() throws Exception {
        assumeWeasyprint(); // Skip if weasyprint is not available

        String htmlContent =
                "<!DOCTYPE html><html><head><title>Basic Test</title></head><body><p>Hello, PDF!</p></body></html>";
        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "fileInput", // parameter name in the controller
                        "BasicTextTest.html", // original filename
                        MediaType.TEXT_HTML_VALUE, // content type
                        htmlContent.getBytes(StandardCharsets.UTF_8) // content as bytes using UTF-8
                        );

        mockMvc.perform(
                        multipart("/api/v1/convert/html/pdf")
                                .file(mockFile)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Length", greaterThan("0")));
    }

    @Test
    public void convertEmptyHtmlFile_shouldReturnSuccessAndEmptyPdf() throws Exception {
        // assumeWeasyprint(); // May or may not need weasyprint depending on handling

        MockMultipartFile emptyFile =
                new MockMultipartFile(
                        "fileInput", "empty.html", MediaType.TEXT_HTML_VALUE, new byte[0]);

        mockMvc.perform(
                        multipart("/api/v1/convert/html/pdf")
                                .file(emptyFile)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
        // .andExpect(header().string("Content-Length", "some_small_value"));
    }

    @Test
    public void missingFileInput_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/convert/html/pdf")
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    // --- 新增测试用例 (使用 String 定义内容) ---

    @Test
    public void convertHtmlWithCss_shouldReturnStyledPdf() throws Exception {
        assumeWeasyprint();

        String htmlContent =
                """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    h1 { color: blue; }
                    p { font-size: 12px; font-family: sans-serif; }
                </style>
            </head>
            <body>
                <h1>Styled Header</h1>
                <p>This paragraph should have specific styling.</p>
            </body>
            </html>
            """; // Using Java Text Blocks for readability

        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "fileInput",
                        "StyledTest.html",
                        MediaType.TEXT_HTML_VALUE,
                        htmlContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/convert/html/pdf").file(mockFile))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    public void convertHtmlWithInlineImage_shouldReturnPdfWithImage() throws Exception {
        assumeWeasyprint();

        // Using a small transparent base64 encoded GIF
        String htmlContent =
                """
            <!DOCTYPE html>
            <html>
            <body>
                <p>Image below:</p>
                <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" alt="Transparent Pixel">
                <p>Text after image.</p>
            </body>
            </html>
            """;

        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "fileInput",
                        "ImageTest.html",
                        MediaType.TEXT_HTML_VALUE,
                        htmlContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/convert/html/pdf").file(mockFile))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Length", greaterThan("0")));
    }

    @Test
    public void uploadNonHtmlFileContent_shouldReturnError() throws Exception {
        assumeWeasyprint();

        String nonHtmlContent = "This is just plain text, not HTML, trying to be converted.";

        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "fileInput",
                        "NotHtml.txt", // Filename suggestion
                        MediaType.TEXT_HTML_VALUE, // Still claim it's HTML
                        nonHtmlContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/convert/html/pdf").file(mockFile))
                .andExpect(status().isInternalServerError()); // Adjust based on actual behavior
    }

    @Test
    public void uploadFileWithUnsupportedContentType_shouldReturnBadRequest() throws Exception {
        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "fileInput",
                        "data.bin",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE, // Non-HTML type
                        "binary data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/convert/html/pdf").file(mockFile))
                .andExpect(status().isBadRequest()); // Or isUnsupportedMediaType() (415)
    }
}
