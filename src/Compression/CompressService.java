package Compression;

/**
 * compressor interface
 * @author Hang Yuan
 */
public interface CompressService {

    /**
     * compress a file
     * @return compressed file size
     */
    public long compress();

    /**
     * decompress a file
     * @return original file size
     */
    public long decompress();
}