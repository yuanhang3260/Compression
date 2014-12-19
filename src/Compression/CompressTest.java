package Compression;

import Compression.CompressService;
import Compression.Compressors;

public class CompressTest {
    public static void main(String[] args) {
        CompressService huftreeCompressor = Compressors.newHuffmanTree("testFile");

        huftreeCompressor.compress();
        huftreeCompressor.decompress();
        return;
    }
}
