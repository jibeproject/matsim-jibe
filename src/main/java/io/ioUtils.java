package io;

import org.apache.log4j.Logger;

import java.io.*;

public class ioUtils {

    public static final Logger log = Logger.getLogger(ioUtils.class);

    public static PrintWriter openFileForSequentialWriting(File outputFile, boolean append) {
        if (outputFile.getParent() != null) {
            File parent = outputFile.getParentFile();
            parent.mkdirs();
        }

        try {
            FileWriter fw = new FileWriter(outputFile, append);
            BufferedWriter bw = new BufferedWriter(fw);
            return new PrintWriter(bw);
        } catch (IOException var5) {
            log.info("Could not open file <" + outputFile.getName() + ">.");
            return null;
        }
    }
}
