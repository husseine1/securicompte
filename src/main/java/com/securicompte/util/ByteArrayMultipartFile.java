package com.securicompte.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Implémentation légère de MultipartFile à partir d'un tableau de bytes.
 * Utilisée pour transmettre le contenu du fichier uploadé au thread asynchrone
 * après la fin de la requête HTTP (le MultipartFile original devient invalide
 * une fois la requête terminée).
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[]  bytes;
    private final String  filename;
    private final String  contentType;

    public ByteArrayMultipartFile(byte[] bytes, String filename, String contentType) {
        this.bytes       = bytes;
        this.filename    = filename;
        this.contentType = contentType;
    }

    @Override public String  getName()             { return "file"; }
    @Override public String  getOriginalFilename() { return filename; }
    @Override public String  getContentType()      { return contentType; }
    @Override public boolean isEmpty()             { return bytes.length == 0; }
    @Override public long    getSize()             { return bytes.length; }
    @Override public byte[]  getBytes()            { return bytes; }
    @Override public InputStream getInputStream()  { return new ByteArrayInputStream(bytes); }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), bytes);
    }
}
