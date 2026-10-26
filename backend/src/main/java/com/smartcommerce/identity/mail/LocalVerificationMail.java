package com.smartcommerce.identity.mail;

import com.smartcommerce.identity.exceptions.IdentityException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Explicit DEV-only private mailbox. Neither file names nor diagnostics contain PII. */
public final class LocalVerificationMail implements VerificationMail {
  private final Path directory;

  public LocalVerificationMail(String configuredDirectory) {
    String localData = System.getenv("LOCALAPPDATA");
    Path defaultRoot =
        localData == null
            ? Path.of(System.getProperty("user.home"), ".local", "share")
            : Path.of(localData);
    Path requested =
        (configuredDirectory.isBlank()
                ? defaultRoot.resolve("SmartCommerce/mail")
                : Path.of(configuredDirectory))
            .toAbsolutePath()
            .normalize();
    try {
      if (requested.toString().toLowerCase(java.util.Locale.ROOT).contains("onedrive")
          || requested.startsWith(workspaceRoot())) throw new IOException("Invalid mailbox");
      Files.createDirectories(requested);
      if (Files.isSymbolicLink(requested)) throw new IOException("Invalid mailbox");
      // Resolve every ancestor, including Windows junctions, before trusting the destination.
      directory = requested.toRealPath();
      if (directory.toString().toLowerCase(java.util.Locale.ROOT).contains("onedrive")
          || directory.startsWith(workspaceRoot())) throw new IOException("Invalid mailbox");
      restrict(directory, true);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot secure DEV mailbox");
    }
  }

  @Override
  public void send(String recipient, String activationLink) {
    write(
        "To: "
            + recipient
            + "\nSubject: Confirme seu e-mail — Smart Commerce\n\nAbra o link e defina sua senha explicitamente:\n"
            + activationLink
            + "\n\nO link expira em 30 minutos. Se não solicitou, ignore esta mensagem.\n");
  }

  @Override
  public void sendPasswordReset(String recipient, String resetLink) {
    write(
        "To: "
            + recipient
            + "\nSubject: Redefina sua senha — Smart Commerce\n\nAbra o link e escolha uma nova senha:\n"
            + resetLink
            + "\n\nO link expira em 30 minutos e só pode ser usado uma vez. Se não pediu, ignore esta mensagem.\n");
  }

  private void write(String message) {
    Path file = directory.resolve(UUID.randomUUID() + ".eml");
    try {
      // Empty file is secured before any sensitive content is written.
      Files.createFile(file);
      restrict(file, false);
      Files.writeString(file, message, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException ignored) {
        /* No sensitive logging. */
      }
      throw new IdentityException(
          IdentityException.Reason.UNAVAILABLE, "Serviço temporariamente indisponível.");
    }
  }

  public void cleanup() {
    try (var files = Files.list(directory)) {
      Instant cutoff = Instant.now().minus(Duration.ofHours(24));
      for (Path file :
          files
              .filter(path -> path.getFileName().toString().matches("[0-9a-f-]{36}\\.eml"))
              .toList()) {
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            && Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)
                .toInstant()
                .isBefore(cutoff)) Files.delete(file);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot clean DEV mailbox");
    }
  }

  private static Path workspaceRoot() throws IOException {
    Path current = Path.of("").toRealPath();
    for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
      if (Files.exists(candidate.resolve(".git"))) return candidate;
    }
    return current;
  }

  private static void restrict(Path path, boolean directory) throws IOException {
    var acl =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (acl != null) {
      AclEntry entry =
          AclEntry.newBuilder()
              .setType(AclEntryType.ALLOW)
              .setPrincipal(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
              .setPermissions(EnumSet.allOf(AclEntryPermission.class))
              .build();
      acl.setAcl(List.of(entry));
    } else {
      Files.setPosixFilePermissions(
          path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
    }
  }
}
