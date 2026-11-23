package com.n0hana.echoes_server.infra.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.n0hana.echoes_server.infra.file.exception.FileValidateException;

public final class UploadFileCopy {
    public static final long MAX_BYTES = 50L * 1024 * 1024;
    public static final String SIZE_LIMIT_MESSAGE = "O limite é de 50 MB";

    private UploadFileCopy() {}

    public static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            if (size > MAX_BYTES) throw new FileValidateException(SIZE_LIMIT_MESSAGE);
            output.write(buffer, 0, count);
        }
        if (size == 0) throw new FileValidateException("Arquivo vazio");
        return size;
    }
}
