package com.smartcommerce.identity.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalVerificationMailTest {
  @TempDir Path temporary;

  @Test
  void localMessageIsPrivateAndWorkspaceIsRejectedEvenWhenRunningFromBackend() throws Exception {
    Path mailbox = temporary.resolve("mail");
    var transport = new LocalVerificationMail(mailbox.toString());
    transport.send(
        "synthetic@example.test", "http://127.0.0.1:3000/confirmar-email#token=synthetic-fixture");
    List<Path> messages;
    try (var files = Files.list(mailbox)) {
      messages = files.toList();
    }
    assertThat(messages).hasSize(1);
    Path message = messages.getFirst();
    assertThat(message.getFileName().toString()).matches("[0-9a-f-]{36}\\.eml");
    assertThat(Files.readString(message))
        .contains("synthetic@example.test", "#token=synthetic-fixture");
    var acl = Files.getFileAttributeView(message, AclFileAttributeView.class);
    if (acl != null) {
      assertThat(acl.getAcl()).hasSize(1);
      assertThat(acl.getAcl().getFirst().principal()).isEqualTo(Files.getOwner(message));
    } else {
      assertThat(Files.getPosixFilePermissions(message))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
    Path root = Path.of("").toRealPath();
    while (root.getParent() != null && !Files.exists(root.resolve(".git"))) root = root.getParent();
    String forbidden = root.resolve(".tools/mail-must-not-exist").toString();
    assertThatThrownBy(() -> new LocalVerificationMail(forbidden))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Files.exists(Path.of(forbidden))).isFalse();
    assertThatThrownBy(
            () -> new LocalVerificationMail(temporary.resolve("OneDrive/mail").toString()))
        .isInstanceOf(IllegalStateException.class);
  }
}
