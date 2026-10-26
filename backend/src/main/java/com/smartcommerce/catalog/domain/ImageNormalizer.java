package com.smartcommerce.catalog.domain;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Decode only bounded raster images, orient JPEGs, scale and re-encode without uploaded metadata.
 */
@Component
public final class ImageNormalizer {
  public static final int MAX_BYTES = 5 * 1024 * 1024;
  private final Semaphore processing = new Semaphore(2);

  /** Product photos: at least 300 pixels on each side. */
  public static final int PHOTO_MIN_SIDE = 300;

  public ImageContent normalize(byte[] bytes, String filename, String declaredType) {
    return normalize(bytes, filename, declaredType, PHOTO_MIN_SIDE);
  }

  /**
   * Same checks with another minimum side, for images that are legitimately small or wide (the
   * store logo, MVP block 1b).
   */
  public ImageContent normalize(byte[] bytes, String filename, String declaredType, int minSide) {
    if (bytes.length == 0 || bytes.length > MAX_BYTES)
      throw invalid("Use uma imagem de até 5 MiB.");
    String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")))
      throw invalid("Use arquivos JPEG (.jpg/.jpeg) ou PNG (.png).");
    if (!processing.tryAcquire())
      throw new ImageException(
          ImageException.Reason.BUSY, "Processamento de imagens ocupado. Tente novamente.");
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("O arquivo não é uma imagem JPEG ou PNG válida.");
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("jpeg") && !format.equals("png"))
          throw invalid("Formato de imagem não permitido.");
        String type = format.equals("jpeg") ? "image/jpeg" : "image/png";
        boolean extensionMatches =
            format.equals("jpeg")
                ? lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                : lower.endsWith(".png");
        if (!extensionMatches || !type.equalsIgnoreCase(declaredType))
          throw invalid("O tipo e a extensão devem corresponder ao conteúdo da imagem.");
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < minSide
            || height < minSide
            || width > 4096
            || height > 4096
            || (long) width * height > 16_000_000)
          throw invalid(
              "Use imagens entre "
                  + minSide
                  + " e 4096 pixels em cada lado, com até 16 megapixels.");
        BufferedImage original = reader.read(0);
        double scale = Math.min(1d, 2048d / Math.max(width, height));
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled =
            new BufferedImage(
                scaledWidth,
                scaledHeight,
                format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
          graphics.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          graphics.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
          graphics.dispose();
          original.flush();
        }
        int orientation = format.equals("jpeg") ? jpegOrientation(bytes) : 1;
        BufferedImage normalized = orient(scaled, orientation);
        try (var output = new BoundedOutput()) {
          if (!ImageIO.write(normalized, format, output))
            throw invalid("Não foi possível processar a imagem.");
          return new ImageContent(
              output.toByteArray(), type, normalized.getWidth(), normalized.getHeight());
        } finally {
          normalized.flush();
          if (normalized != scaled) scaled.flush();
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException exception) {
      throw invalid("Imagem inválida, truncada ou grande demais após processamento.");
    } finally {
      processing.release();
    }
  }

  private static BufferedImage orient(BufferedImage source, int orientation) {
    if (orientation < 2 || orientation > 8) return source;
    int w = source.getWidth(), h = source.getHeight();
    var target =
        new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, source.getType());
    AffineTransform transform =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
          case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
          case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
          case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
          default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
    var graphics = target.createGraphics();
    try {
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  // Bounded EXIF IFD0 orientation only; no XML, external data or unbounded offsets are processed.
  private static int jpegOrientation(byte[] data) {
    for (int offset = 2; offset + 4 <= data.length; ) {
      if ((data[offset] & 255) != 255) break;
      int marker = data[offset + 1] & 255;
      if (marker == 0xda || marker == 0xd9) break;
      int length = ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255);
      if (length < 2 || offset + 2L + length > data.length) break;
      int end = offset + 2 + length, tiff = offset + 10;
      if (marker == 0xe1
          && length >= 16
          && data[offset + 4] == 'E'
          && data[offset + 5] == 'x'
          && data[offset + 6] == 'i'
          && data[offset + 7] == 'f'
          && data[offset + 8] == 0
          && data[offset + 9] == 0) {
        boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
        if (!little && !(data[tiff] == 'M' && data[tiff + 1] == 'M')) return 1;
        if (number(data, tiff + 2, 2, little) != 42) return 1;
        long relative = number(data, tiff + 4, 4, little);
        if (relative < 8 || relative + tiff + 2 > end) return 1;
        int directory = tiff + (int) relative, count = (int) number(data, directory, 2, little);
        if (count > 256 || directory + 2L + count * 12L > end) return 1;
        for (int i = 0; i < count; i++) {
          int entry = directory + 2 + i * 12;
          if (number(data, entry, 2, little) == 0x112
              && number(data, entry + 2, 2, little) == 3
              && number(data, entry + 4, 4, little) == 1)
            return (int) number(data, entry + 8, 2, little);
        }
      }
      offset = end;
    }
    return 1;
  }

  private static long number(byte[] data, int offset, int size, boolean little) {
    long result = 0;
    for (int i = 0; i < size; i++)
      result = (result << 8) | (data[offset + (little ? size - 1 - i : i)] & 255);
    return result;
  }

  private static ImageException invalid(String message) {
    return new ImageException(ImageException.Reason.INVALID, message);
  }

  private static final class BoundedOutput extends ByteArrayOutputStream {
    @Override
    public synchronized void write(int b) {
      if (count >= MAX_BYTES) throw invalid("Imagem normalizada maior que 5 MiB.");
      super.write(b);
    }

    @Override
    public synchronized void write(byte[] bytes, int off, int len) {
      if (len > MAX_BYTES - count) throw invalid("Imagem normalizada maior que 5 MiB.");
      super.write(bytes, off, len);
    }
  }
}
