package Huffman;

import java.io.Serializable;
import java.util.PriorityQueue;
import java.io.IOException;
import java.io.FileReader;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;


/**
 * HuffmanTree
 * @author Hang Yuan
 */
public class HuffmanTree implements Serializable {

    /*
     * Huffman Tree Node
     */   
    class TreeNode implements Comparable {
        char encode;
        TreeNode left;
        TreeNode right;
        int count;

        @Override
        public int compareTo(Object other){
            return this.count - ((TreeNode)other).count;
        }
    }

    String fileName = null;
    TreeNode root = null;
    int nodeNum = 0;
    int fileSize = 0;

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public void HuffmanTree(String fileName) {
        this.fileName = fileName;
    }

    /**
     * create Huffman Tree
     */
    public void createHuffmanTree() {
        // read file and count number of all bytes
        File file = new File(fileName);
        try {
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName)); 
            byte[] buf = new byte[1024];  
            int len = ins.read(buf, 0, buf.length);  
            
            ins.close();
        } 
        catch (IOException e) {
           e.printStackTrace();
        }
    }

    /**
     * compressFile
     * @return compressed file size
     */
    public int compressFile() {
        return 0;
    }

}