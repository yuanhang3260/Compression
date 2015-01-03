package Compression;

import Compression.CompressService;
import Compression.Compressors;

public class CompressTest {
    public static void main(String[] args) {
        CompressService arithCompressor = Compressors.newArithCoder("testFile");

        arithCompressor.compress();
        arithCompressor.decompress();
        return;
    }
}
