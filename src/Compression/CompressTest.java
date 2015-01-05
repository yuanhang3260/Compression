package Compression;

import Compression.CompressService;
import Compression.Compressors;

public class CompressTest {
    public static void main(String[] args) {
        CompressService compressor = Compressors.newDictLZW("testFile");

        compressor.compress();
        compressor.decompress();
        return;
    }
}
