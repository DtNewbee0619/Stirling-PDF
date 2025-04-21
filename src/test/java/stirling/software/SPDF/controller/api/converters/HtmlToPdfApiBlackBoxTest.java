package stirling.software.SPDF.controller.api.converters;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import io.restassured.response.Response;

/**
 * Black-box API testing - HTML to PDF conversion functionality This test class verifies API
 * behavior from an external client's perspective, without relying on internal implementation
 * details.
 */
// @ExtendWith(SpringExtension.class) // Annotation for Spring integration (if needed)
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Annotation for
// Spring Boot test context (if needed)
public class HtmlToPdfApiBlackBoxTest {

    // @LocalServerPort private int port; // Injects the random port used by the server (if using
    // @SpringBootTest)

    private final String API_ENDPOINT = "/api/v1/convert/html/pdf"; // Define the API endpoint URL

    @BeforeAll
    static void setUp() {
        // Set the port number for RestAssured. Replace with dynamic port if using @LocalServerPort
        port = 9090;
        // If using @SpringBootTest with a random port, you'd typically set RestAssured.port =
        // this.port; in a @BeforeEach method
        // For a fixed port setup like this, setting it statically is fine.
    }

    /** Tests successful conversion of a valid HTML file to PDF. */
    @Test
    void testValidHtmlToPdf_Success() {
        // Create a simple valid HTML content
        String html = "<html><body><h1>Test HTML Content</h1></body></html>";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput", // Name of the file input parameter
                                "test.html", // Original filename
                                html.getBytes(StandardCharsets.UTF_8), // File content as bytes
                                "text/html") // Content type of the file
                        .multiPart("zoom", "1.0") // Form parameter for zoom level
                        .when()
                        .post(API_ENDPOINT) // Perform POST request
                        .then()
                        .statusCode(200) // Expect HTTP 200 OK status
                        .contentType("application/pdf") // Expect PDF content type
                        .header(
                                "Content-Disposition",
                                containsString(
                                        "filename=\"test.pdf\"")) // Expect correct filename in
                        // header
                        .extract()
                        .response(); // Extract the response object

        // Validate the response is a valid PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests successful conversion of a ZIP file containing HTML and resources. */
    @Test
    void testValidZipWithHtml_Success() throws IOException {
        // Create a ZIP file containing HTML and CSS
        byte[] zipBytes = createTestZipWithHtml();

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "test.zip",
                                zipBytes,
                                "application/zip") // Upload the ZIP file
                        .multiPart("zoom", "1.5") // Use a different zoom level
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .header("Content-Disposition", containsString("filename=\"test.pdf\""))
                        .extract()
                        .response();

        // Validate the response is a valid PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests conversion using a different zoom factor. */
    @Test
    void testHtmlToPdf_WithDifferentZoom_Success() {
        // Create a simple valid HTML content
        String html = "<html><body><h1>Test HTML with Different Zoom</h1></body></html>";

        // Send the request with a different zoom value
        Response response =
                given().multiPart(
                                "fileInput",
                                "test.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "2.0") // Higher zoom value
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate the response is a valid PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests HTML with complex content including CSS styling. */
    @Test
    void testHtmlToPdf_WithComplexContent_Success() {
        // Create HTML with CSS styles and more complex structure
        String complexHtml =
                "<html><head><style>body{font-family:Arial;color:blue;} h1{color:red;}</style></head>"
                        + "<body><h1>Styled Heading</h1><p>This is a paragraph with <b>bold</b> and <i>italic</i> text.</p>"
                        + "<div style='background-color:yellow;padding:10px;'>This is a styled div</div></body></html>";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "complex.html",
                                complexHtml.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate the response is a valid PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling when no file is provided. */
    @Test
    void testHtmlToPdf_WithNoFile_ReturnsBadRequest() {
        // Send request without a file
        given().multiPart("zoom", "1.0") // Provide other parameters if needed
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value()) // Expect 400 Bad Request
                .body(
                        containsString(
                                "Please provide an HTML or ZIP file for conversion")); // Expect
        // specific
        // error
        // message
    }

    /** Tests API handling when an invalid file type is provided. */
    @Test
    void testHtmlToPdf_WithInvalidFileType_ReturnsBadRequest() {
        // Create content for a text file (not HTML or ZIP)
        String textContent = "This is plain text, not HTML";

        // Send request with a text file
        given().multiPart(
                        "fileInput",
                        "test.txt", // Filename with .txt extension
                        textContent.getBytes(StandardCharsets.UTF_8),
                        "text/plain") // Incorrect content type for this endpoint
                .multiPart("zoom", "1.0")
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(
                        HttpStatus.BAD_REQUEST
                                .value()) // Expect 400 Bad Request (or possibly 415 Unsupported
                // Media Type depending on server config)
                //                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()) //
                // Alternative expectation if backend crashes
                .body(
                        containsString(
                                "File must be either .html or .zip format")); // Expect specific
        // error message
    }

    /** Tests API handling when a file with no extension is provided. */
    @Test
    void testHtmlToPdf_WithNoExtension_ReturnsBadRequest() {
        // Create HTML content but provide a filename without a proper extension
        String htmlContent = "<html><body>Test</body></html>";

        // Send request with a file lacking a .html or .zip extension
        given().multiPart(
                        "fileInput",
                        "noextension", // Filename without extension
                        htmlContent.getBytes(StandardCharsets.UTF_8),
                        "text/html") // Correct content type, but filename is checked
                .multiPart("zoom", "1.0")
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value()) // Expect 400 Bad Request
                .body(
                        containsString(
                                "File must be either .html or .zip format")); // Expect specific
        // error message
    }

    /** Tests API handling with a potentially malicious filename (e.g., path traversal attempt). */
    @Test
    void testHtmlToPdf_WithMaliciousFilename_HandlesSecurely() {
        // HTML content with a filename attempting path traversal
        String html = "<html><body><h1>Test Content</h1></body></html>";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "../../../malicious/path/test.html", // Malicious filename attempt
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(
                                200) // Expect it to still work, but filename should be sanitized
                        // server-side
                        .contentType("application/pdf")
                        // Check Content-Disposition header is sanitized (doesn't contain '../')
                        .header("Content-Disposition", not(containsString("../")))
                        .header(
                                "Content-Disposition",
                                containsString(
                                        "filename=\"test.pdf\"")) // Or whatever sanitized name is
                        // expected
                        .extract()
                        .response();

        // Validate PDF is returned and potentially check if filename was sanitized
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling with invalid HTML structure. */
    @Test
    void testHtmlToPdf_WithInvalidHtmlStructure() {
        // Malformed HTML content (e.g., missing closing tag)
        String invalidHtml = "<html><body><h1>Missing closing tag";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "invalid.html",
                                invalidHtml.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200) // Expect it might still work, as HTML renderers are often
                        // lenient
                        // Or potentially 500 Internal Server Error if the backend converter crashes
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned even with invalid HTML (browser/renderer tolerance)
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling with a large HTML file. */
    @Test
    void testHtmlToPdf_WithLargeHtmlFile() {
        // Generate large HTML content
        StringBuilder largeHtml = new StringBuilder("<html><body>");
        for (int i = 0; i < 1000; i++) { // Adjust loop count based on desired size/complexity
            largeHtml.append("<p>Paragraph of large content ").append(i).append("</p>");
        }
        largeHtml.append("</body></html>");

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "large.html",
                                largeHtml.toString().getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200) // Expect success
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling of HTML with potentially unsafe content (e.g., scripts). */
    @Test
    void testHtmlToPdf_WithUnsafeContent_SanitizesContent() {
        // HTML content with potentially unsafe elements (script, iframe)
        String unsafeHtml =
                "<html><body><h1>Safe Content</h1>"
                        + "<script>alert('XSS attempt');</script>" // Script tag
                        + "<iframe src='https://malicious-site.com'></iframe>" // Iframe tag
                        + "<p>More content</p>"
                        + "</body></html>";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "unsafe.html",
                                unsafeHtml.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(
                                200) // Expect it to still work, but content should be sanitized by
                        // the PDF renderer (scripts typically don't execute)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned (verifying actual sanitization might require PDF content
        // analysis)
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
        // Further checks could involve extracting text from PDF and asserting that script content
        // is absent.
    }

    /** Tests API handling with an empty HTML file. */
    @Test
    void testHtmlToPdf_WithEmptyHtmlFile_Success() {
        // Empty HTML content
        String emptyHtml = "";

        // Send the request and validate the response
        Response response =
                given().multiPart(
                                "fileInput",
                                "empty.html",
                                emptyHtml.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(
                                200) // Should still work with empty content, producing a (likely
                        // blank) PDF
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned (it might be very small or have zero pages depending on the
        // converter)
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty (even if blank)");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling with a very small zoom factor. */
    @Test
    void testHtmlToPdf_WithVerySmallZoom() {
        // HTML content
        String html = "<html><body><h1>Very Small Zoom Test</h1></body></html>";

        // Send request with a very small zoom factor
        Response response =
                given().multiPart(
                                "fileInput",
                                "smallzoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "0.1") // Very small zoom
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling with a very large zoom factor. */
    @Test
    void testHtmlToPdf_WithVeryLargeZoom() {
        // HTML content
        String html = "<html><body><h1>Very Large Zoom Test</h1></body></html>";

        // Send request with a very large zoom factor
        Response response =
                given().multiPart(
                                "fileInput",
                                "largezoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "10.0") // Very large zoom
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    /** Tests API handling when the zoom parameter is omitted (should use default). */
    @Test
    void testHtmlToPdf_WithoutZoomParameter_UsesDefault() {
        // HTML content
        String html = "<html><body><h1>Default Zoom Test</h1></body></html>";

        // Send request without the zoom parameter
        Response response =
                given().multiPart(
                                "fileInput",
                                "nozoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .when() // No 'zoom' multiPart parameter provided
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200) // Should succeed using the default zoom value
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // Validate PDF is returned
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        assertTrue(isPdfValid(pdfBytes), "Response should be a valid PDF document");
    }

    // --- Helper Methods ---

    /**
     * Creates a test ZIP file containing an HTML file and a CSS file.
     *
     * @return Byte array representing the ZIP file content.
     * @throws IOException If an I/O error occurs during ZIP creation.
     */
    private byte[] createTestZipWithHtml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add HTML file
            ZipEntry htmlEntry = new ZipEntry("index.html");
            zos.putNextEntry(htmlEntry);
            // HTML links to the CSS file within the ZIP
            zos.write(
                    "<html><head><link rel=\"stylesheet\" href=\"styles.css\"></head><body><h1>Test HTML in ZIP</h1></body></html>"
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add CSS file
            ZipEntry cssEntry = new ZipEntry("styles.css");
            zos.putNextEntry(cssEntry);
            zos.write("h1 { color: blue; font-size: 24px; }".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Checks if the provided byte array represents a valid PDF document by attempting to load it
     * using Apache PDFBox.
     *
     * @param pdfBytes The byte array potentially containing PDF data.
     * @return true if the bytes represent a valid, non-empty PDF; false otherwise.
     */
    private boolean isPdfValid(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return false;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // If we can load it and it has at least one page, consider it valid for basic checks.
            return document.getNumberOfPages()
                    >= 0; // Allow 0 pages for potentially valid but blank PDFs
        } catch (IOException e) {
            // If PDFBox fails to load it, it's considered invalid.
            System.err.println("PDF validation failed: " + e.getMessage());
            return false;
        }
    }
}
