package stirling.software.SPDF.utils;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ImageToPdfApiTest {

    @LocalServerPort int port;

    @BeforeEach
    public void setupRestAssured() {
        // Set RestAssured to "http://localhost:{port}"
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /**
     * Helper for sending API request to upload files, with optional fitOption/colorType parameters
     */
    private Response callApi(List<File> files, String fitOption, String colorType) {
        RequestSpecification req = given();
        if (fitOption != null) req.param("fitOption", fitOption);
        if (colorType != null) req.param("colorType", colorType);
        for (File f : files) req.multiPart("fileInput", f);
        return req.post("/api/v1/convert/img/pdf");
    }

    /**
     * Helper for concurrent testing: spawn `threads` concurrent calls with same files + params, and
     * assert each returns expectedStatus
     */
    private void concurrentTest(
            List<File> files, String fitOption, String colorType, int threads, int expectedStatus)
            throws InterruptedException, ExecutionException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<Response>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(exec.submit(() -> callApi(files, fitOption, colorType)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(60, TimeUnit.SECONDS), "Threads did not finish");

        for (Future<Response> f : futures) {
            Response res = f.get();
            res.then().statusCode(expectedStatus);
        }
    }

    // T1: Small JPG x1, threads=1, MaintainAspectRatio, Colour → 200 + PDF
    @Test
    @DisplayName("T1: SJ small jpg×1, t=1, MaintainAspectRatio, Colour")
    void T1() {
        File file = new File("src/test/resources/testImages/SG.gif");
        callApi(Collections.singletonList(file), "maintainAspectRatio", "Colour")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    // T2: Medium GIF x1, threads=5, FitPageToImage, Greyscale → all 200
    @Test
    @DisplayName("T2: MG medium gif×1, t=5, FitPageToImage, Greyscale")
    void T2() throws Exception {
        List<File> files =
                Collections.singletonList(new File("src/test/resources/testImages/MG.gif"));
        concurrentTest(files, "FitPageToImage", "Greyscale", 5, 200);
    }

    // T3: Large PNG x1, threads=10, FillPage, Black and White → all 413
    @Test
    @DisplayName("T3: LP large png×1, t=10, FillPage, Black & White")
    void T3() throws Exception {
        List<File> files =
                Collections.singletonList(new File("src/test/resources/testImages/LP.png"));
        concurrentTest(files, "FillPage", "blackwhite", 10, 413);
    }

    // T4: Medium JPG x5, threads=10, FillPage, Greyscale → all 200
    @Test
    @DisplayName("T4: MJ medium jpg×5, t=10, FillPage, Greyscale")
    void T4() throws Exception {
        File file = new File("src/test/resources/testImages/MJ.jpg");
        List<File> files = Collections.nCopies(5, file);
        concurrentTest(files, "FillPage", "Greyscale", 10, 200);
    }

    // T5: Large GIF x5, threads=1, MaintainAspectRatio, Black and White → 413
    @Test
    @DisplayName("T5: LG large gif×5, t=1, MaintainAspectRatio, Black & White")
    void T5() {
        File file = new File("src/test/resources/testImages/LG.gif");
        List<File> files = Collections.nCopies(5, file);
        callApi(files, "maintainAspectRatio", "blackwhite").then().statusCode(413);
    }

    // T6: Small PNG x5, threads=5, FitPageToImage, Colour → all 200
    @Test
    @DisplayName("T6: SP small png×5, t=5, FitPageToImage, Colour")
    void T6() throws Exception {
        File file = new File("src/test/resources/testImages/SP.png");
        List<File> files = Collections.nCopies(5, file);
        concurrentTest(files, "FitPageToImage", "Colour", 5, 200);
    }

    // T7: Large JPG x10, threads=5, FitPageToImage, Black and White → all 413
    @Test
    @DisplayName("T7: LJ large jpg×10, t=5, FitPageToImage, Black & White")
    void T7() throws Exception {
        File file = new File("src/test/resources/testImages/LJ.jpg");
        List<File> files = Collections.nCopies(10, file);
        concurrentTest(files, "FitPageToImage", "blackwhite", 5, 413);
    }

    // T8: Small GIF x10, threads=10, FillPage, Colour → all 200
    @Test
    @DisplayName("T8: SG small gif×10, t=10, FillPage, Colour")
    void T8() throws Exception {
        File file = new File("src/test/resources/testImages/SG.gif");
        List<File> files = Collections.nCopies(10, file);
        concurrentTest(files, "FillPage", "Colour", 10, 200);
    }

    // T9: Medium PNG x10, threads=1, MaintainAspectRatio, Greyscale → 200
    @Test
    @DisplayName("T9: MP medium png×10, t=1, MaintainAspectRatio, Greyscale")
    void T9() {
        File file = new File("src/test/resources/testImages/MP.png");
        List<File> files = Collections.nCopies(10, file);
        callApi(files, "maintainAspectRatio", "Greyscale")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    // T10: Small JPG x5, threads=10, FillPage, Black and White → all 200
    @Test
    @DisplayName("T10: SJ small jpg×5, t=10, FillPage, Black & White")
    void T10() throws Exception {
        File file = new File("src/test/resources/testImages/SJ.jpg");
        List<File> files = Collections.nCopies(5, file);
        concurrentTest(files, "FillPage", "blackwhite", 10, 200);
    }

    // T11: Medium GIF x10, threads=1, MaintainAspectRatio, Greyscale → 200
    @Test
    @DisplayName("T11: MG medium gif×10, t=1, MaintainAspectRatio, Greyscale")
    void T11() {
        File file = new File("src/test/resources/testImages/MG.gif");
        List<File> files = Collections.nCopies(10, file);
        callApi(files, "maintainAspectRatio", "Greyscale")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    // T12: Large PNG x1, threads=5, FitPageToImage, Colour → all 413
    @Test
    @DisplayName("T12: LP large png×1, t=5, FitPageToImage, Colour")
    void T12() throws Exception {
        List<File> files =
                Collections.singletonList(new File("src/test/resources/testImages/LP.png"));
        concurrentTest(files, "FitPageToImage", "Colour", 5, 413);
    }
}
