package io.github.lmysticl.dlqdesk.inbox;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class PayloadCodec {
  public static final int MAX_BYTES = 262144;
  private final JsonMapper json;

  public PayloadCodec(JsonMapper json) {
    this.json = json;
  }

  public String checkedJson(String value) {
    if (value == null
        || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
        || !isJson(value))
      throw new IllegalArgumentException("A valid JSON payload of at most 256 KiB is required");
    return encode(value.getBytes(StandardCharsets.UTF_8));
  }

  public boolean isJson(String value) {
    try {
      return json.readTree(value) != null;
    } catch (JacksonException failure) {
      return false;
    }
  }

  public static String encode(byte[] value) {
    return value == null ? null : Base64.getEncoder().encodeToString(value);
  }

  public static byte[] decode(String value) {
    return value == null ? null : Base64.getDecoder().decode(value);
  }

  public static String utf8(byte[] value) {
    if (value == null) return null;
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(value))
          .toString();
    } catch (CharacterCodingException failure) {
      return null;
    }
  }

  public static String displayText(byte[] value) {
    String text = utf8(value);
    return text == null
            || text.chars()
                .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
        ? null
        : text;
  }

  public static String hash(String base64) {
    var digest = digest();
    field(digest, decode(base64));
    return HexFormat.of().formatHex(digest.digest());
  }

  public static void field(MessageDigest digest, byte[] value) {
    digest.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
    if (value != null) digest.update(value);
  }

  public static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("JVM lacks SHA-256", failure);
    }
  }
}
