package Compression;

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

    Mode crtMode = Mode.Compress;
    double compressRate = 1;
    
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