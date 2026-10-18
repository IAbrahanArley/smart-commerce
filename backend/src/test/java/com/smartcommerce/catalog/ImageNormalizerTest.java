package com.smartcommerce.catalog;

import static org.assertj.core.api.Assertions.*;

import com.smartcommerce.catalog.domain.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageNormalizerTest {
  private final ImageNormalizer normalizer = new ImageNormalizer();

  @Test
  void rejectsFakeMismatchedTruncatedAndOversizedFiles() throws Exception {
    byte[] png = picture("png", 300, 300);
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "<svg onload='alert(1)'/>".getBytes(), "image.png", "image/png"))
        .isInstanceOf(ImageException.class);
    assertThatThrownBy(() -> normalizer.normalize(png, "image.jpg", "image/jpeg"))
        .isInstanceOf(ImageException.class);
    assertThatThrownBy(() -> normalizer.normalize(png, "image.png", "text/html"))
        .isInstanceOf(ImageException.class);
    assertThatThrownBy(
            () -> normalizer.normalize(java.util.Arrays.copyOf(png, 30), "image.png", "image/png"))
        .isInstanceOf(ImageException.class);
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    new byte[ImageNormalizer.MAX_BYTES + 1], "image.png", "image/png"))
        .isInstanceOf(ImageException.class);
  }

  @Test
  void validatesDimensionsBeforeDecodeAndBoundsTheOutput() throws Exception {
    assertThatThrownBy(
            () -> normalizer.normalize(picture("png", 299, 300), "image.png", "image/png"))
        .isInstanceOf(ImageException.class);
    byte[] dimensions = picture("png", 300, 300);
    ByteBuffer.wrap(dimensions, 16, 8).putInt(50000).putInt(50000);
    assertThatThrownBy(() -> normalizer.normalize(dimensions, "image.png", "image/png"))
        .isInstanceOf(ImageException.class);
    ImageContent result = normalizer.normalize(picture("png", 3000, 600), "image.png", "image/png");
    assertThat(result.width()).isEqualTo(2048);
    assertThat(result.height()).isEqualTo(410);
    assertThat(result.bytes()).hasSizeLessThanOrEqualTo(ImageNormalizer.MAX_BYTES);
    assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes())).getWidth()).isEqualTo(2048);
  }

  @Test
  void discardsTrailingPayloadAndPreservesPngTransparency() throws Exception {
    byte[] source = picture("png", 300, 300);
    var combined = new ByteArrayOutputStream();
    combined.write(source);
    combined.write("<script>payload</script>".getBytes(StandardCharsets.UTF_8));
    var result = normalizer.normalize(combined.toByteArray(), "../../../image.png", "image/png");
    assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("payload");
    assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes())).getColorModel().hasAlpha())
        .isTrue();
  }

  @Test
  void honorsAllExifOrientationsAndStripsExif() throws Exception {
    byte[] source = picture("jpeg", 300, 400);
    for (int orientation = 1; orientation <= 8; orientation++) {
      byte[] tiff =
          ByteBuffer.allocate(26)
              .order(ByteOrder.LITTLE_ENDIAN)
              .putShort((short) 0x4949)
              .putShort((short) 42)
              .putInt(8)
              .putShort((short) 1)
              .putShort((short) 0x112)
              .putShort((short) 3)
              .putInt(1)
              .putShort((short) orientation)
              .putShort((short) 0)
              .putInt(0)
              .array();
      var data = new ByteArrayOutputStream();
      data.write(source, 0, 2);
      data.write(new byte[] {(byte) 255, (byte) 225, 0, 34});
      data.write(new byte[] {'E', 'x', 'i', 'f', 0, 0});
      data.write(tiff);
      data.write(source, 2, source.length - 2);
      var result = normalizer.normalize(data.toByteArray(), "image.jpg", "image/jpeg");
      assertThat(result.width()).isEqualTo(orientation >= 5 ? 400 : 300);
      assertThat(result.height()).isEqualTo(orientation >= 5 ? 300 : 400);
      assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }
  }

  static byte[] picture(String format, int width, int height) throws IOException {
    BufferedImage source =
        new BufferedImage(
            width,
            height,
            format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();
    ImageIO.write(source, format, output);
    source.flush();
    return output.toByteArray();
  }
}
