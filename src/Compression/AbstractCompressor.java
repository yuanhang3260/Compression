package Compression;

import java.io.File;
import Compression.CompressService;

/**
 * abstract compressor
 * @author Hang Yuan
 */
public abstract class AbstractCompressor implements CompressService {
    
    public enum Mode {
        Compress,
        Decompress
    }

    String fileName = null;
    String zipFileName = null;
    long fileSize = 0;
    long compressedSize = 0;
    Mode crtMode = Mode.Compress;
    double compressRate = 1;
    
    public AbstractCompressor(String pathName, String postFix) {
        int postFixLen = postFix.length();

        if (pathName.length() >= (postFixLen + 2) &&
            pathName.substring(pathName.length() - (postFixLen + 1), pathName.length()).equals(postFix)) 
        {
            setDecompressMode();
            zipFileName = pathName;
            fileName = pathName.substring(0, pathName.length() - (postFixLen + 1));
        }
        else {
            fileName = pathName;
            zipFileName = fileName + "." + postFix;
        }
        
        // get original file size
        File file = new File(fileName);
        fileSize = file.length();
    }

    /**
     * get compress rate
     * @return compress rate
     */
    public double getCompressRate() {
        return compressRate;
    }

    /**
     * set compress rate
     * @param rate compress rate
     */
    public void setCompressRate(double rate) {
        this.compressRate = rate;
    }

    /**
     * get current mode
     */
    public Mode mode() {
        return this.crtMode;
    }

    /**
     * set as compress mode 
     */
    public void setCompressMode() {
        crtMode = Mode.Compress;
    }

    /**
     * set as compress mode 
     */
    public void setDecompressMode() {
        crtMode = Mode.Decompress;
    }
}