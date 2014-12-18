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
        byte b;
        TreeNode left = null;
        TreeNode right = null;
        int count;

        public TreeNode(byte b, int count) {
            this.b = b;
            this.count = count;
        }

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
            
            // count number of each byte
            int[] counts = new int[256];
            long len = file.length();
            for (int i = 0; i < len; i++) {
                byte b = (byte)ins.read();
                counts[(int)(b&0x0FF)]++;
            }

            // create tree nodes and put them in a min heap
            PriorityQueue<TreeNode> heap = new PriorityQueue<TreeNode>();
            for (int i = 0; i < 256; i++) {
                if (counts[i] > 0) {
                    TreeNode node = new TreeNode((byte)(i&0xFF), counts[i]);
                    heap.add(node);
                }
            }
            nodeNum = heap.size();
            
            // create Huffman tree
            for (int i = 0; i < nodeNum - 1; i++) {
                TreeNode node1 = heap.poll();
                TreeNode node2 = heap.poll();
                TreeNode newNode = new TreeNode((byte)0, node1.count + node2.count);
                newNode.left = node1;
                newNode.right = node2;
                heap.add(newNode);
            }
            this.root = heap.poll(); // final node is root
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