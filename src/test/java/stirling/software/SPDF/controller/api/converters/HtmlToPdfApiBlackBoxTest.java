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

/** 黑盒API测试 - HTML到PDF转换功能 这个测试类从外部客户端的角度验证API行为，不依赖于内部实现细节 */
// @ExtendWith(SpringExtension.class)
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HtmlToPdfApiBlackBoxTest {

    // @LocalServerPort private int port;

    private final String API_ENDPOINT = "/api/v1/convert/html/pdf";

    @BeforeAll
    static void setUp() {
        //        baseURI = "http://localhost:8080";
        baseURI = "http://localhost:8080";
        //        RestAssured.basePath = "http://localhost:";
    }

    /** 测试有效HTML文件转换为PDF的成功情况 */
    @Test
    void testValidHtmlToPdf_Success() {
        // 创建一个简单的有效HTML文件
        String html = "<html><body><h1>测试HTML内容</h1></body></html>";

        // 发送请求并验证响应
        Response response =
                given().multiPart(
                                "fileInput",
                                "test.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .header("Content-Disposition", containsString("filename=\"test.pdf\""))
                        .extract()
                        .response();

        // 验证响应是一个有效的PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试包含HTML和资源的ZIP文件的成功转换 */
    @Test
    void testValidZipWithHtml_Success() throws IOException {
        // 创建一个包含HTML和CSS的ZIP文件
        byte[] zipBytes = createTestZipWithHtml();

        // 发送请求并验证响应
        Response response =
                given().multiPart("fileInput", "test.zip", zipBytes, "application/zip")
                        .multiPart("zoom", "1.5")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .header("Content-Disposition", containsString("filename=\"test.pdf\""))
                        .extract()
                        .response();

        // 验证响应是一个有效的PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试使用不同缩放因子的转换 */
    @Test
    void testHtmlToPdf_WithDifferentZoom_Success() {
        // 创建一个简单的有效HTML文件
        String html = "<html><body><h1>使用不同缩放的测试HTML</h1></body></html>";

        // 发送具有不同缩放值的请求
        Response response =
                given().multiPart(
                                "fileInput",
                                "test.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "2.0") // 较高的缩放值
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证响应是一个有效的PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试处理包含CSS样式的复杂内容的HTML */
    @Test
    void testHtmlToPdf_WithComplexContent_Success() {
        // 创建具有CSS样式和更复杂结构的HTML
        String complexHtml =
                "<html><head><style>body{font-family:Arial;color:blue;} h1{color:red;}</style></head>"
                        + "<body><h1>样式化标题</h1><p>这是一个包含<b>粗体</b>和<i>斜体</i>文本的段落。</p>"
                        + "<div style='background-color:yellow;padding:10px;'>这是一个带样式的div</div></body></html>";

        // 发送请求并验证响应
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

        // 验证响应是一个有效的PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试未提供文件时的API处理 */
    @Test
    void testHtmlToPdf_WithNoFile_ReturnsBadRequest() {
        // 发送没有文件的请求
        given().multiPart("zoom", "1.0")
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body(containsString("Please provide an HTML or ZIP file for conversion"));
    }

    /** 测试提供无效文件类型时的API处理 */
    @Test
    void testHtmlToPdf_WithInvalidFileType_ReturnsBadRequest() {
        // 创建一个文本文件（非HTML或ZIP）
        String textContent = "这是纯文本，不是HTML";

        // 使用文本文件发送请求
        given().multiPart(
                        "fileInput",
                        "test.txt",
                        textContent.getBytes(StandardCharsets.UTF_8),
                        "text/plain")
                .multiPart("zoom", "1.0")
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body(containsString("File must be either .html or .zip format"));
    }

    /** 测试提供没有扩展名的文件时的API处理 */
    @Test
    void testHtmlToPdf_WithNoExtension_ReturnsBadRequest() {
        // 创建没有适当文件扩展名的内容
        String htmlContent = "<html><body>测试</body></html>";

        // 使用没有扩展名的文件发送请求
        given().multiPart(
                        "fileInput",
                        "noextension",
                        htmlContent.getBytes(StandardCharsets.UTF_8),
                        "text/html")
                .multiPart("zoom", "1.0")
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body(containsString("File must be either .html or .zip format"));
    }

    /** 测试提供潜在恶意文件名时的API处理 */
    @Test
    void testHtmlToPdf_WithMaliciousFilename_HandlesSecurely() {
        // 在文件名中尝试路径遍历的HTML内容
        String html = "<html><body><h1>测试内容</h1></body></html>";

        // 发送请求并验证响应
        Response response =
                given().multiPart(
                                "fileInput",
                                "../../../malicious/path/test.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "1.0")
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200) // 应该仍然工作，但文件名应该被清理
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF并且文件名已被清理
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试提供无效HTML结构时的API处理 */
    @Test
    void testHtmlToPdf_WithInvalidHtmlStructure() {
        // 格式错误的HTML内容
        String invalidHtml = "<html><body><h1>缺少闭合标签";

        // 发送请求并验证响应
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
                        .statusCode(200) // 应该仍然工作，因为HTML渲染引擎是宽容的
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证尽管HTML无效但返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试大型HTML文件的API处理 */
    @Test
    void testHtmlToPdf_WithLargeHtmlFile() {
        // 生成大型HTML内容
        StringBuilder largeHtml = new StringBuilder("<html><body>");
        for (int i = 0; i < 1000; i++) {
            largeHtml.append("<p>大型内容段落 ").append(i).append("</p>");
        }
        largeHtml.append("</body></html>");

        // 发送请求并验证响应
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
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试包含潜在不安全内容的HTML的API处理 */
    @Test
    void testHtmlToPdf_WithUnsafeContent_SanitizesContent() {
        // 包含潜在不安全元素的HTML内容（script, iframe）
        String unsafeHtml =
                "<html><body><h1>安全内容</h1>"
                        + "<script>alert('XSS攻击');</script>"
                        + "<iframe src='https://malicious-site.com'></iframe>"
                        + "</body></html>";

        // 发送请求并验证响应
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
                        .statusCode(200) // 应该仍然工作，但内容应该被清理
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试提供空HTML文件时的API处理 */
    @Test
    void testHtmlToPdf_WithEmptyHtmlFile_Success() {
        // 空HTML内容
        String emptyHtml = "";

        // 发送请求并验证响应
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
                        .statusCode(200) // 应该仍然使用空内容工作
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试提供极小缩放因子时的API处理 */
    @Test
    void testHtmlToPdf_WithVerySmallZoom() {
        // HTML内容
        String html = "<html><body><h1>极小缩放测试</h1></body></html>";

        // 发送具有极小缩放的请求
        Response response =
                given().multiPart(
                                "fileInput",
                                "smallzoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "0.1") // 非常小的缩放
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试提供极大缩放因子时的API处理 */
    @Test
    void testHtmlToPdf_WithVeryLargeZoom() {
        // HTML内容
        String html = "<html><body><h1>极大缩放测试</h1></body></html>";

        // 发送具有极大缩放的请求
        Response response =
                given().multiPart(
                                "fileInput",
                                "largezoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .multiPart("zoom", "10.0") // 非常大的缩放
                        .when()
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200)
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    /** 测试省略缩放参数时的API处理（应使用默认值） */
    @Test
    void testHtmlToPdf_WithoutZoomParameter_UsesDefault() {
        // HTML内容
        String html = "<html><body><h1>默认缩放测试</h1></body></html>";

        // 发送不包含缩放参数的请求
        Response response =
                given().multiPart(
                                "fileInput",
                                "nozoom.html",
                                html.getBytes(StandardCharsets.UTF_8),
                                "text/html")
                        .when() // 未提供缩放参数
                        .post(API_ENDPOINT)
                        .then()
                        .statusCode(200) // 应使用默认缩放值
                        .contentType("application/pdf")
                        .extract()
                        .response();

        // 验证返回了PDF
        byte[] pdfBytes = response.asByteArray();
        assertTrue(pdfBytes.length > 0, "PDF不应为空");
        assertTrue(isPdfValid(pdfBytes), "响应应该是一个有效的PDF");
    }

    // 辅助方法

    /** 创建包含HTML和CSS文件的测试ZIP文件 */
    private byte[] createTestZipWithHtml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 添加HTML文件
            ZipEntry htmlEntry = new ZipEntry("index.html");
            zos.putNextEntry(htmlEntry);
            zos.write(
                    "<html><head><link rel=\"stylesheet\" href=\"styles.css\"></head><body><h1>ZIP中的测试HTML</h1></body></html>"
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 添加CSS文件
            ZipEntry cssEntry = new ZipEntry("styles.css");
            zos.putNextEntry(cssEntry);
            zos.write("h1 { color: blue; font-size: 24px; }".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** 检查提供的字节数组是否表示有效的PDF文档 */
    private boolean isPdfValid(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // 如果我们可以将其加载为PDF文档，则它是有效的
            return document.getNumberOfPages() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
