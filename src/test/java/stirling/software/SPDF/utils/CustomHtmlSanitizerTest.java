package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class CustomHtmlSanitizerTest {

    @Test
    void testSanitize_RemovesUnsafeContent() {
        // Arrange
        String unsafeHtml =
                "<html><body><script>alert('XSS');</script><h1>Safe Content</h1></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(unsafeHtml);

        // Assert
        assertFalse(sanitizedHtml.contains("script"));
        assertFalse(sanitizedHtml.contains("alert('XSS')"));
        assertTrue(sanitizedHtml.contains("<h1>Safe Content</h1>"));
    }

    @Test
    void testSanitize_PreservesFormattingElements() {
        // Arrange
        String html = "<html><body><b>Bold</b> <i>Italic</i> <u>Underline</u></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("<b>Bold</b>"));
        assertTrue(sanitizedHtml.contains("<i>Italic</i>"));
        assertTrue(sanitizedHtml.contains("<u>Underline</u>"));
    }

    @Test
    void testSanitize_PreservesBlockElements() {
        // Arrange
        String html = "<html><body><div>Div</div><p>Paragraph</p><h1>Heading</h1></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("<div>Div</div>"));
        assertTrue(sanitizedHtml.contains("<p>Paragraph</p>"));
        assertTrue(sanitizedHtml.contains("<h1>Heading</h1>"));
    }

    @Test
    void testSanitize_PreservesStyles() {
        // Arrange
        String html = "<html><body><div style=\"color:red\">Red text</div></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("style=\"color:red\""));
    }

    @Test
    void testSanitize_PreservesLinks() {
        // Arrange
        String html = "<html><body><a href=\"https://example.com\">Link</a></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("href=\"https://example.com\""));
    }

    @Test
    void testSanitize_PreservesTables() {
        // Arrange
        String html = "<html><body><table><tr><td>Cell</td></tr></table></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("<table>"));
        assertTrue(sanitizedHtml.contains("<tr>"));
        assertTrue(sanitizedHtml.contains("<td>Cell</td>"));
    }

    @Test
    void testSanitize_PreservesImages() {
        // Arrange
        String html = "<html><body><img src=\"image.jpg\" alt=\"Image\"></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertTrue(sanitizedHtml.contains("<img"));
        assertTrue(sanitizedHtml.contains("src=\"image.jpg\""));
        assertTrue(sanitizedHtml.contains("alt=\"Image\""));
    }

    @Test
    void testSanitize_RemovesNoScriptTags() {
        // Arrange
        String html =
                "<html><body><noscript>JavaScript is disabled</noscript><div>Content</div></body></html>";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(html);

        // Assert
        assertFalse(sanitizedHtml.contains("<noscript>"));
        assertFalse(sanitizedHtml.contains("JavaScript is disabled"));
        assertTrue(sanitizedHtml.contains("<div>Content</div>"));
    }

    @Test
    void testSanitize_HandlesEmptyInput() {
        // Arrange
        String emptyHtml = "";

        // Act
        String sanitizedHtml = CustomHtmlSanitizer.sanitize(emptyHtml);

        // Assert
        assertEquals("", sanitizedHtml);
    }

    @Test
    void testSanitize_HandlesNullInput() {
        // Arrange
        String nullHtml = null;

        // Act & Assert
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class, () -> CustomHtmlSanitizer.sanitize(nullHtml));
    }
}
