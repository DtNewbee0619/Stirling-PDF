package stirling.software.SPDF.utils;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.NestedServletException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
@AutoConfigureMockMvc(addFilters = false) // Skip security filters for integration test
public class ConvertMarkdownToPdfIntegrationTest {

    @Autowired private MockMvc mockMvc;

    /**
     * Test case: Valid Markdown file input
     *
     * <p>This test verifies that a proper Markdown file is converted successfully to PDF. If
     * weasyprint is missing, this test will be skipped automatically.
     */
    @Test
    public void convertValidMarkdownToPdf_shouldReturnPdfBytes() throws Exception {
        // Skip if weasyprint is not on PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "weasyprint");
            int exit = pb.start().waitFor();
            org.junit.jupiter.api.Assumptions.assumeTrue(exit == 0, "Skipping: weasyprint not installed");
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Skipping: weasyprint check failed");
            return;
        }

        // Load sample Markdown file
        ClassPathResource res = new ClassPathResource("Markdown.md");
        MockMultipartFile mockFile = new MockMultipartFile(
            "fileInput",
            "Markdown.md",
            "text/markdown",
            res.getInputStream()
        );

        mockMvc.perform(
                multipart("/api/v1/convert/markdown/pdf")
                    .file(mockFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isOk())
            // Expect PDF content type, not octet-stream
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            // Optional: verify Content-Disposition has the correct filename
            .andExpect(header().string(
                HttpHeaders.CONTENT_DISPOSITION,
                containsString("filename=\"Markdown.pdf\"")
            ));
    }

    /**
     * Test case: Empty Markdown file
     *
     * <p>❌ This test will fail unless the source code explicitly checks for empty input. Source
     * code should handle fileInput.isEmpty() and return HTTP 400.
     */
    @Test
    public void convertEmptyMarkdownFile_shouldReturnNonEmptyPdf() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "fileInput",
            "empty.md",
            "text/markdown",
            new byte[0]
        );

        MvcResult mvcResult = mockMvc.perform(
                multipart("/api/v1/convert/markdown/pdf")
                    .file(emptyFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header()
                .string("Content-Disposition", containsString("filename=\"empty.pdf\"")))
            .andReturn();

        // 4. 响应体非空，且以 "%PDF" 开头
        byte[] body = mvcResult.getResponse().getContentAsByteArray();
        assertTrue(body.length > 0, "PDF body should not be empty");
        String pdfHeader = new String(body, 0, Math.min(body.length, 4), StandardCharsets.UTF_8);
        assertEquals("%PDF", pdfHeader, "Response should start with PDF header");
    }

    /**
     * Test case: Missing fileInput field
     *
     * <p>❌ This test will fail with NullPointerException unless the controller checks fileInput !=
     * null. Controller should return HTTP 400 Bad Request for missing file input.
     */
    @Test
    public void missingFileInput_shouldThrowIllegalArgumentException() throws Exception {
        // 发起不带 fileInput 的请求
        ServletException ex = assertThrows(
            ServletException.class,
            () -> mockMvc.perform(
                    multipart("/api/v1/convert/markdown/pdf")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andReturn()  // 一定要加 .andReturn() 触发执行
        );

        Throwable root = ex.getRootCause();
        assertNotNull(root, "Should have a root cause");
        assertTrue(root instanceof IllegalArgumentException,
            () -> "Expected IllegalArgumentException, but was " + root.getClass().getSimpleName());

        // 验证异常消息
        assertEquals(
            "Please provide a Markdown file for conversion.",
            root.getMessage(),
            "Exception message should indicate missing Markdown file"
        );
    }
}
