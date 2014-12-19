package Compression;

import Compression.HuffmanTree;

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
}