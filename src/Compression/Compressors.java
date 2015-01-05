package Compression;

import Compression.HuffmanTree;
import Compression.ArithCoder;
import Compression.DictLZW;

/**
 *  Factory class: producing compressors
 *  @author Hang Yuan
 */
public class Compressors {
    /**
     * create a huffman tree compressor
     */
    public static HuffmanTree newHuffmanTree(String fileName) {
        return new HuffmanTree(fileName);
    }

    /**
     * create a huffman tree compressor
     */
    public static ArithCoder newArithCoder(String fileName) {
        return new ArithCoder(fileName);
    }

    /**
     * create a dict LZW compressor
     */
    public static DictLZW newDictLZW(String fileName) {
        return new DictLZW(fileName);
    }


}