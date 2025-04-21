package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Comprehensive unit tests for ImageProcessingUtils covering normal cases, boundary cases, and
 * property-based tests for all methods.
 */
class ImageProcessingUtilsComprehansiveTest {

    @Test
    void forceLoadUtilsClass() {
        // Force class initialization to cover the no-args constructor and any static blocks
        new ImageProcessingUtils();
    }

    // ---------- Tests for convertColorType ----------

    /** Test that converting an RGB image to greyscale results in TYPE_BYTE_GRAY. */
    @Test
    void testConvertColorType_toGreyscale() {
        BufferedImage src = new BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = ImageProcessingUtils.convertColorType(src, "greyscale");
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, dst.getType(), "Should convert to greyscale");
    }

    /** Test that converting an RGB image to black-and-white results in TYPE_BYTE_BINARY. */
    @Test
    void testConvertColorType_toBlackWhite() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = ImageProcessingUtils.convertColorType(src, "blackwhite");
        assertEquals(
                BufferedImage.TYPE_BYTE_BINARY, dst.getType(), "Should convert to black-and-white");
    }

    /** Test that passing an unknown colorType returns the original image unchanged. */
    @Test
    void testConvertColorType_default() {
        BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = ImageProcessingUtils.convertColorType(src, "unknown");
        assertSame(src, dst, "Unknown colorType should return the original image");
    }

    /**
     * Property-based test for convertColorType: - Image dimensions should remain unchanged. - For
     * "greyscale", each pixel's R=G=B. - For "blackwhite", each pixel is either 0 or 255. - For any
     * other type, original pixels remain unchanged.
     */
    @Property
    void propConvertColorType_properties(
            @ForAll @IntRange(min = 1, max = 50) int w,
            @ForAll @IntRange(min = 1, max = 50) int h,
            @ForAll("colorTypes") String type) {
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        // Fill up to 10 random pixels with random colors
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < Math.min(10, w * h); i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            int rgb = (rnd.nextInt(0xFFFFFF) | 0xFF000000);
            src.setRGB(x, y, rgb);
        }

        BufferedImage dst = ImageProcessingUtils.convertColorType(src, type);

        // Dimensions should not change
        assertEquals(w, dst.getWidth());
        assertEquals(h, dst.getHeight());

        if ("greyscale".equals(type)) {
            // Greyscale: R=G=B for every pixel
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int p = dst.getRGB(x, y);
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    assertEquals(r, g, "In greyscale, R and G must match");
                    assertEquals(g, b, "In greyscale, G and B must match");
                }
            }
        } else if ("blackwhite".equals(type)) {
            // Black-and-white: each pixel is either 0 or 255
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int p = dst.getRGB(x, y) & 0xFF;
                    assertTrue(p == 0 || p == 255, "In blackwhite, pixel must be 0 or 255");
                }
            }
        } else {
            // Default: sampled random pixels should remain the same
            for (int i = 0; i < Math.min(10, w * h); i++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                assertEquals(
                        src.getRGB(x, y),
                        dst.getRGB(x, y),
                        "For default type, pixels should be unchanged");
            }
        }
    }

    @Provide
    Arbitrary<String> colorTypes() {
        // Provide both valid and invalid colorType values
        return Arbitraries.of("greyscale", "blackwhite", "foo", null);
    }

    // ---------- Tests for getImageData ----------

    /** Test getImageData on a greyscale image (DataBufferByte). Expect 1 byte per pixel. */
    @Test
    void testGetImageData_byteBuffer() {
        BufferedImage img = new BufferedImage(3, 3, BufferedImage.TYPE_BYTE_GRAY);
        byte[] data = ImageProcessingUtils.getImageData(img);
        assertEquals(3 * 3, data.length, "Greyscale image should produce 1 byte per pixel");
    }

    /**
     * Property-based test for getImageData on an INT_RGB image (DataBufferInt). Expect 4 bytes per
     * pixel.
     */
    @Property
    void propGetImageData_intBuffer(
            @ForAll @IntRange(min = 1, max = 50) int w,
            @ForAll @IntRange(min = 1, max = 50) int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        byte[] data = ImageProcessingUtils.getImageData(img);
        assertEquals(w * h * 4, data.length, "RGB image should produce 4 bytes per pixel");
    }

    /**
     * Test getImageData on a TYPE_INT_ARGB image: - Length = w*h*4 - Byte array can be reassembled
     * back into the original ARGB values.
     */
    @Test
    void testGetImageData_intBuffer_content() {
        int w = 2, h = 1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xAA112233);
        img.setRGB(1, 0, 0xBB445566);

        byte[] data = ImageProcessingUtils.getImageData(img);
        assertEquals(w * h * 4, data.length, "IntBuffer branch should return 4 bytes/pixel");

        int p0 = ByteBuffer.wrap(data, 0, 4).getInt();
        assertEquals(0xAA112233, p0, "First pixel ARGB should match original");

        int p1 = ByteBuffer.wrap(data, 4, 4).getInt();
        assertEquals(0xBB445566, p1, "Second pixel ARGB should match original");
    }

    /**
     * Test getImageData on an image type that triggers the generic (else) branch (neither
     * DataBufferByte nor DataBufferInt). Expect 3 bytes per pixel (RGB).
     */
    @Test
    void testGetImageData_elseBranch_lengthOnly() {
        int w = 7, h = 5;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);

        byte[] data = ImageProcessingUtils.getImageData(img);

        assertEquals(
                w * h * 3,
                data.length,
                String.format(
                        "The else branch should return %d bytes, but it actually returns %d bytes.",
                        w * h * 3, data.length));
    }

    // ---------- Tests for extractImageOrientation ----------

    /** Test extractImageOrientation for known EXIF orientation tags 6,3,8,1. */
    @Test
    void testExtractImageOrientation_knownTags() throws Exception {
        try (MockedStatic<ImageMetadataReader> ms = Mockito.mockStatic(ImageMetadataReader.class)) {
            Metadata meta = Mockito.mock(Metadata.class);
            ExifSubIFDDirectory dir = Mockito.mock(ExifSubIFDDirectory.class);
            ms.when(() -> ImageMetadataReader.readMetadata(Mockito.any(InputStream.class)))
                    .thenReturn(meta);
            Mockito.when(meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class)).thenReturn(dir);

            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)).thenReturn(6);
            assertEquals(90, ImageProcessingUtils.extractImageOrientation(fakeStream()));
            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)).thenReturn(3);
            assertEquals(180, ImageProcessingUtils.extractImageOrientation(fakeStream()));
            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)).thenReturn(8);
            assertEquals(270, ImageProcessingUtils.extractImageOrientation(fakeStream()));
            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)).thenReturn(1);
            assertEquals(0, ImageProcessingUtils.extractImageOrientation(fakeStream()));
        }
    }

    /**
     * Test extractImageOrientation when an unknown tag is encountered. The implementation logs a
     * warning and returns 0.
     */
    @Test
    void testExtractImageOrientation_unknownTag() throws Exception {
        try (MockedStatic<ImageMetadataReader> ms = Mockito.mockStatic(ImageMetadataReader.class)) {
            Metadata meta = Mockito.mock(Metadata.class);
            ExifSubIFDDirectory dir = Mockito.mock(ExifSubIFDDirectory.class);
            ms.when(() -> ImageMetadataReader.readMetadata(Mockito.any(InputStream.class)))
                    .thenReturn(meta);
            Mockito.when(meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class)).thenReturn(dir);
            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)).thenReturn(99);

            assertEquals(
                    0,
                    ImageProcessingUtils.extractImageOrientation(fakeStream()),
                    "Unknown tag should default to 0°");
        }
    }

    /**
     * Test extractImageOrientation when reading metadata throws an exception. Expect a default
     * return of 0.
     */
    @Test
    void testExtractImageOrientation_metadataException() throws Exception {
        try (MockedStatic<ImageMetadataReader> ms = Mockito.mockStatic(ImageMetadataReader.class)) {
            Metadata meta = Mockito.mock(Metadata.class);
            ExifSubIFDDirectory dir = Mockito.mock(ExifSubIFDDirectory.class);
            ms.when(() -> ImageMetadataReader.readMetadata((InputStream) Mockito.any()))
                    .thenReturn(meta);
            Mockito.when(meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class)).thenReturn(dir);
            Mockito.when(dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION))
                    .thenThrow(new MetadataException("bad"));

            assertEquals(0, ImageProcessingUtils.extractImageOrientation(fakeStream()));
        }
    }

    private InputStream fakeStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    // ---------- Tests for applyOrientation ----------

    /** Test that applyOrientation with 0° returns the original image. */
    @Test
    void testApplyOrientation_zero() {
        BufferedImage img = new BufferedImage(4, 5, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = ImageProcessingUtils.applyOrientation(img, 0);
        assertSame(img, out, "0° should return the original image");
    }

    /** Test that applyOrientation with 90° swaps width and height. */
    @Test
    void testApplyOrientation_90Degrees() {
        BufferedImage img = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = ImageProcessingUtils.applyOrientation(img, 90);
        assertEquals(3, out.getWidth(), "Width and height should swap after 90° rotation");
        assertEquals(2, out.getHeight(), "Height and width should swap after 90° rotation");
    }

    /**
     * Property-based test for applyOrientation on square images and orthogonal angles: width and
     * height should remain equal for 0°, 90°, 180°, 270°.
     */
    @Property
    void propApplyOrientation_squareAndOrthogonal(
            @ForAll @IntRange(min = 1, max = 50) int size,
            @ForAll("orthogonalAngles") double angle) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = ImageProcessingUtils.applyOrientation(img, angle);
        assertEquals(size, out.getWidth(), "Width should remain the same for orthogonal angles");
        assertEquals(size, out.getHeight(), "Height should remain the same for orthogonal angles");
    }

    @Provide
    Arbitrary<Double> orthogonalAngles() {
        return Arbitraries.of(0.0, 90.0, 180.0, 270.0);
    }

    // ---------- Tests for loadImageWithExifOrientation ----------

    /** Test that loadImageWithExifOrientation throws IOException for non-image files. */
    @Test
    void testLoadImageWithExifOrientation_invalid() {
        MockMultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "not image".getBytes());
        assertThrows(
                IOException.class,
                () -> ImageProcessingUtils.loadImageWithExifOrientation(file),
                "Non-image input should throw IOException");
    }

    /**
     * Test loadImageWithExifOrientation end-to-end with mocked EXIF orientation. We mock
     * extractImageOrientation to return 90°, then verify the resulting image dimensions.
     */
    @Test
    void testLoadImageWithExifOrientation_withMockedExifOrientation() throws Exception {
        BufferedImage testImage = new BufferedImage(4, 5, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        MockMultipartFile mockFile =
                new MockMultipartFile("file", "test.png", "image/png", imageBytes);

        try (MockedStatic<ImageProcessingUtils> mockedUtils =
                Mockito.mockStatic(ImageProcessingUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockedUtils
                    .when(
                            () ->
                                    ImageProcessingUtils.extractImageOrientation(
                                            Mockito.any(InputStream.class)))
                    .thenReturn(90.0);

            BufferedImage result = ImageProcessingUtils.loadImageWithExifOrientation(mockFile);

            int origW = testImage.getWidth(); // 4
            int origH = testImage.getHeight(); // 5
            int resW = result.getWidth();
            int resH = result.getHeight();

            System.out.printf(
                    "origW=%d, resultW=%d, origH=%d, resultH=%d%n", origW, resW, origH, resH);

            assertEquals(origW, resW);
            assertEquals(origH, resH);
        }
    }
}
