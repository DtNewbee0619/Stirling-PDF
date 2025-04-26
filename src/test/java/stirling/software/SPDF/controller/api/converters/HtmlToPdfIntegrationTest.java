package stirling.software.SPDF.controller.api.converters;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import jakarta.servlet.ServletException;

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

    /*
     * Test case: Valid HTML file input
     *
     * <p>This test verifies that a proper HTML file is converted successfully to PDF. If
     * weasyprint is missing, this test will be skipped automatically.
     */
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

    /*
     * Test case: Empty HTML file
     *
     * <p>This test verifies that an empty HTML file is handled correctly. If weasyprint is missing,
     * this test will be skipped automatically.
     */
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

    /*
     * Test case: Invalid HTML file
     *
     * <p>❌ This test will fail if weasyprint is not available or if the HTML cannot be rendered.
     * Ensure that weasyprint is installed and accessible in the environment where this test runs.
     */
    @Test
    public void missingFileInput_shouldReturnBadRequest() throws Exception {
        ServletException ex =
                assertThrows(
                        ServletException.class,
                        () ->
                                mockMvc.perform(
                                                multipart("/api/v1/convert/html/pdf")
                                                        .contentType(MediaType.MULTIPART_FORM_DATA))
                                        .andReturn());

        Throwable root = ex.getRootCause();
        assertNotNull(root, "Should have a root cause");
        assertTrue(
                root instanceof IllegalArgumentException,
                () ->
                        "Expected IllegalArgumentException, but was "
                                + root.getClass().getSimpleName());

        assertEquals(
                "Please provide an HTML or ZIP file for conversion.",
                root.getMessage(),
                "Exception message should indicate missing Markdown file");
    }

    /*
     * Test case: HTML with CSS
     *
     * <p>❌ This test will fail if weasyprint is not available or if the CSS cannot be rendered.
     * Ensure that weasyprint is installed and accessible in the environment where this test runs.
     */
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

    /*
     * Test case: HTML with inline image
     *
     * <p>❌ This test will fail if weasyprint is not available or if the image cannot be rendered.
     * Ensure that weasyprint is installed and accessible in the environment where this test runs.
     */
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
}
