package stirling.software.SPDF.utils;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for Image-to-PDF conversion. Each test case is annotated above with a table:
 * Case ID | File Count | Color Mode | DPI | Output Type | Page Selection | Expected Result | Test
 * Objective
 */
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
@AutoConfigureMockMvc(addFilters = false) // Skip security filters
class ConvertIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Nested
    @DisplayName("Invalid Input Scenarios")
    class InvalidScenarios {

        /** TC1: Zero files – no upload should return HTTP 400 Bad Request */
        @Test
        void tc1_zeroFiles_shouldBadRequest() throws Exception {
            mockMvc.perform(multipart("/api/v1/convert/img/pdf"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * TC9: Invalid fitOption – valid image with invalid fitOption should return HTTP 400 Bad
         * Request
         */
        @Test
        void tc9_invalidFitOption_shouldBadRequest() throws Exception {
            // Prepare: load test image
            InputStream is = getClass().getResourceAsStream("/testImages/test2.jpg");
            assertNotNull(is, "Test resource test2.jpg must exist");
            MockMultipartFile jpg =
                    new MockMultipartFile("fileInput", "test2.jpg", "image/jpeg", is);

            // Execute: send invalid fitOption
            mockMvc.perform(
                            multipart("/api/v1/convert/img/pdf")
                                    .file(jpg)
                                    .param("fitOption", "invalid"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * TC10: Invalid colorType – valid image with invalid colorType should return HTTP 400 Bad
         * Request
         */
        @Test
        void tc10_invalidColorType_shouldBadRequest() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test2.jpg");
            assertNotNull(is, "Test resource test2.jpg must exist");
            MockMultipartFile jpg =
                    new MockMultipartFile("fileInput", "test2.jpg", "image/jpeg", is);

            mockMvc.perform(
                            multipart("/api/v1/convert/img/pdf")
                                    .file(jpg)
                                    .param("colorType", "foo"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Valid Scenarios")
    class ValidScenarios {

        /**
         * TC2: Single-file default conversion Scenario: upload one JPEG without optional parameters
         * Expectation: - HTTP 200 OK - Content-Type: application/pdf - Content-Disposition filename
         * contains “_converted.pdf”
         */
        @Test
        void tc2_jpegDefault_shouldReturnPdf() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test2.jpg");
            assertNotNull(is, "Test resource test2.jpg must exist");
            MockMultipartFile jpg =
                    new MockMultipartFile("fileInput", "test2.jpg", "image/jpeg", is);

            mockMvc.perform(multipart("/api/v1/convert/img/pdf").file(jpg))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(
                            header().string(
                                            "Content-Disposition",
                                            containsString("test2_converted.pdf")));
        }

        /**
         * TC3: Greyscale mode Scenario: upload one PNG with colorType=greyscale Expectation: - HTTP
         * 200 OK - Content-Type: application/pdf - PDF has 1 page - Embedded image uses DeviceGray
         * color space
         */
        @Test
        void tc3_greyscale_shouldReturnGrayPdf() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test1.png");
            assertNotNull(is, "Test resource test1.png must exist");
            MockMultipartFile png =
                    new MockMultipartFile("fileInput", "test1.png", "image/png", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(png)
                                            .param("colorType", "greyscale"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages(), "PDF should contain exactly one page");
                PDImageXObject img = extractFirstImage(doc);
                assertEquals(
                        "DeviceGray",
                        img.getColorSpace().getName(),
                        "Greyscale mode should use DeviceGray color space");
            }
        }

        /**
         * TC5: Black and white mode Scenario: upload one PNG with colorType=blackwhite Expectation:
         * - HTTP 200 OK - Content-Type: application/pdf - PDF has 1 page - Embedded image is 1-bit
         * black and white
         */
        @Test
        void tc5_blackwhite_shouldReturnBlackWhitePdf() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test1.png");
            assertNotNull(is, "Test resource test1.png must exist");
            MockMultipartFile png =
                    new MockMultipartFile("fileInput", "test1.png", "image/png", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(png)
                                            .param("colorType", "blackwhite"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages(), "PDF should contain exactly one page");
                PDImageXObject img = extractFirstImage(doc);
                assertEquals(
                        1,
                        img.getBitsPerComponent(),
                        "Black and white mode should produce a 1-bit image");
            }
        }

        /**
         * TC6: Black and white mode with JPEG Scenario: upload one JPEG with colorType=blackwhite
         * Expectation: - HTTP 200 OK - Content-Type: application/pdf - PDF has 1 page - Embedded
         * image is 1-bit black and white
         */
        @Test
        void tc6_blackwhite_jpeg_shouldReturnBlackWhitePdf() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test2.jpg");
            assertNotNull(is, "Test resource test2.jpg must exist");
            MockMultipartFile jpg =
                    new MockMultipartFile("fileInput", "test2.jpg", "image/jpeg", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(jpg)
                                            .param("colorType", "blackwhite"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages(), "PDF should contain exactly one page");
                PDImageXObject img = extractFirstImage(doc);
                assertEquals(
                        1,
                        img.getBitsPerComponent(),
                        "Black and white mode should produce a 1-bit image");
            }
        }

        /**
         * TC7: Black and white mode with GIF Scenario: upload one GIF with colorType=blackwhite
         * Expectation: - HTTP 200 OK - Content-Type: application/pdf - PDF has 1 page - Embedded
         * image is 1-bit black and white
         */
        @Test
        void tc7_blackwhite_gif_shouldReturnBlackWhitePdf() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test3.gif");
            assertNotNull(is, "Test resource test3.gif must exist");
            MockMultipartFile gif =
                    new MockMultipartFile("fileInput", "test3.gif", "image/gif", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(gif)
                                            .param("colorType", "blackwhite"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages(), "PDF should contain exactly one page");
                PDImageXObject img = extractFirstImage(doc);
                assertEquals(
                        1,
                        img.getBitsPerComponent(),
                        "Black and white mode should produce a 1-bit image");
            }
        }

        /**
         * TC6: Two-file merge (default separate=false) Scenario: upload two images Expectation: -
         * HTTP 200 OK - Content-Type: application/pdf - PDF contains 2 pages, one per image
         */
        @Test
        void tc6_twoFiles_shouldMergeIntoSinglePdfWithTwoPages() throws Exception {
            InputStream a = getClass().getResourceAsStream("/testImages/test2.jpg");
            InputStream b = getClass().getResourceAsStream("/testImages/test4.jpg");
            assertNotNull(a, "Test resource test2.jpg must exist");
            assertNotNull(b, "Test resource test4.jpg must exist");
            MockMultipartFile f1 = new MockMultipartFile("fileInput", "a.jpg", "image/jpeg", a);
            MockMultipartFile f2 = new MockMultipartFile("fileInput", "b.jpg", "image/jpeg", b);

            byte[] pdfBytes =
                    mockMvc.perform(multipart("/api/v1/convert/img/pdf").file(f1).file(f2))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            // Verify page count using PDFBox
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(2, doc.getNumberOfPages(), "Merged PDF should contain two pages");
            }
        }

        /** TC_Color: color mode – first image should use DeviceRGB */
        @Test
        void tcColorType_color_shouldUseDeviceRGB() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test2.jpg");
            assertNotNull(is, "Test resource test2.jpg must exist");
            MockMultipartFile jpg =
                    new MockMultipartFile("fileInput", "test2.jpg", "image/jpeg", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(jpg)
                                            .param("colorType", "color"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                PDImageXObject img = extractFirstImage(doc);
                assertEquals(
                        "DeviceRGB",
                        img.getColorSpace().getName(),
                        "Color mode should use DeviceRGB color space");
            }
        }

        /** TC_FIT_1: fitOption=fillPage – image should be stretched and aspect ratio changed */
        @Test
        void tc_fitOption_fillPage_shouldStretchImage() throws Exception {
            InputStream is = getClass().getResourceAsStream("/testImages/test1.png");
            assertNotNull(is, "Test resource test1.png must exist");
            MockMultipartFile img =
                    new MockMultipartFile("fileInput", "test1.png", "image/png", is);

            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(img)
                                            .param("fitOption", "fillPage")
                                            .param("colorType", "color"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                PDImageXObject obj = extractFirstImage(doc);
                float ratio = (float) obj.getWidth() / obj.getHeight();
                assertNotEquals(
                        2.0f, ratio, 0.01f, "fillPage mode should alter the image aspect ratio");
            }
        }

        /** TC_FIT_3: fitOption=maintainAspectRatio – image should maintain original aspect ratio */
        @Test
        void tc_fitOption_maintainAspectRatio_shouldPreserveRatio() throws Exception {
            BufferedImage buf =
                    ImageIO.read(getClass().getResourceAsStream("/testImages/test1.png"));
            float expectedRatio = (float) buf.getWidth() / buf.getHeight();

            // 2. 调用接口拿到 PDF
            byte[] pdfBytes =
                    mockMvc.perform(
                                    multipart("/api/v1/convert/img/pdf")
                                            .file(
                                                    new MockMultipartFile(
                                                            "fileInput",
                                                            "test1.png",
                                                            "image/png",
                                                            Files.readAllBytes(
                                                                    Paths.get(
                                                                            getClass()
                                                                                    .getResource(
                                                                                            "/testImages/test1.png")
                                                                                    .toURI()))))
                                            .param("fitOption", "maintainAspectRatio")
                                            .param("colorType", "color"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();

            // 3. 验证嵌入的图片对象的宽高比
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                PDImageXObject obj = extractFirstImage(doc);
                float actualRatio = (float) obj.getWidth() / obj.getHeight();
                assertEquals(
                        expectedRatio,
                        actualRatio,
                        0.01f,
                        "maintainAspectRatio shoule matian orignal ratio");
            }
        }

        /** Helper: extract the first image object from the first page of a PDF document */
        private PDImageXObject extractFirstImage(PDDocument doc) throws IOException {
            PDResources resources = doc.getPage(0).getResources();
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xobj = resources.getXObject(name);
                if (xobj instanceof PDImageXObject) {
                    return (PDImageXObject) xobj;
                }
            }
            throw new AssertionError("No image XObject found on the first page");
        }
    }
}
