package org.hanwen.common;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

import javax.imageio.ImageIO;

public final class FileRules {
    static boolean prefix(byte[] bytes, byte... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[i] != expected[i]) return false;
        return true;
    }

    public static String type(String name, byte[] data) throws IOException {
        String ext =
                name.contains(".")
                        ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                        : "";
        String mime = null;
        if (ext.equals("pdf")
                && prefix(data, (byte) '%', (byte) 'P', (byte) 'D', (byte) 'F', (byte) '-'))
            mime = "application/pdf";
        if (ext.equals("png")
                && prefix(
                        data,
                        (byte) 137,
                        (byte) 80,
                        (byte) 78,
                        (byte) 71,
                        (byte) 13,
                        (byte) 10,
                        (byte) 26,
                        (byte) 10)) mime = "image/png";
        if (Set.of("jpg", "jpeg").contains(ext) && prefix(data, (byte) 255, (byte) 216, (byte) 255))
            mime = "image/jpeg";
        if (ext.equals("mp3")
                && (prefix(data, (byte) 'I', (byte) 'D', (byte) '3')
                        || data.length > 2 && (data[0] & 255) == 255 && (data[1] & 224) == 224))
            mime = "audio/mpeg";
        if (ext.equals("wav")
                && data.length >= 12
                && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WAVE"))
            mime = "audio/wav";
        if (ext.equals("txt")) {
            try {
                var text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(data))
                                .toString();
                if (text.indexOf('\0') >= 0) throw ApiException.bad("文本文件含无效字符");
                mime = "text/plain;charset=UTF-8";
            } catch (CharacterCodingException e) {
                throw ApiException.bad("文本请保存为UTF-8编码");
            }
        }
        if (mime == null) throw ApiException.bad("仅支持内容与扩展名一致的PDF、PNG、JPG、MP3、WAV和UTF-8文本");
        if (mime.startsWith("image/")) {
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw ApiException.bad("图片格式无法读取");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels > 25000000L) throw ApiException.bad("图片尺寸过大，请压缩后上传");
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                throw ApiException.bad("图片内容损坏，无法读取");
            }
        }
        return mime;
    }
}
