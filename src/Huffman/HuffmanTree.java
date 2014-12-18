package Huffman;

import java.io.Serializable;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;

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
        ArrayList<Byte> encode = null;

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
    long fileSize = 0; // of byte
    long compressedSize = 0; // of bit

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
        fileSize = file.length();
        try {
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));
            
            // count number of each byte
            int[] counts = new int[256];
            for (int i = 0; i < fileSize; i++) {
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

            // biuld Huffman tree
            if (nodeNum == 1) { // edge case
                TreeNode node1 = heap.poll();
                TreeNode node2 = new TreeNode((byte)0, 0);
                TreeNode newNode = new TreeNode((byte)0, node1.count + node2.count);
                newNode.left = node1;
                newNode.right = node2;
                heap.add(newNode);
            }
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
     * | original size | compressed size | treeNode num | tree nodes  | bytes |
     * |   8 bytes     |     8 bytes     |    4bytes    |   {b, cnt}  | ..... |
     * @return compressed file size
     */
    public int compressFile() {
        File file = new File(fileName);
        try {
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(fileName + "zzip"));

            // write original file size
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(fileSize);
            outs.write(bBuf.array(), 0, 8);
            // this 8 bytes is temporarily saved for compressed size
            outs.write(bBuf.array(), 0, 8);
            // write number of treeNodes
            bBuf = ByteBuffer.allocate(4);
            bBuf.putLong(nodeNum);
            outs.write(bBuf.array(), 0, 4);
            // traverse the huffman tree and write treeNodes to .zzip file
            HashMap<Byte, TreeNode> map = new HashMap<Byte, TreeNode>();
            Stack<TreeNode> stack = new Stack<TreeNode>();
            stack.push(root);
            root.encode = new ArrayList<Byte>();
            while (!stack.isEmpty()) {
                TreeNode node = stack.pop();
                // write leave node to .zzip file
                if (node.left == null && node.right == null) {
                    bBuf = ByteBuffer.allocate(5);
                    bBuf.put(node.b);
                    bBuf.putInt(1, node.count);
                    outs.write(bBuf.array(), 0, 5);
                    // add this leave to HashMap
                    map.put(node.b, node);
                }
                // push left and right child
                if (node.left != null) {
                    node.left.encode = new ArrayList<Byte>();
                    node.left.encode.addAll(node.encode);
                    node.left.encode.add((byte)0);
                    stack.push(node.left);
                }
                if (node.right != null) {
                    node.right.encode = new ArrayList<Byte>();
                    node.right.encode.addAll(node.encode);
                    node.right.encode.add((byte)1);
                    stack.push(node.right);
                }
            }

            // start encoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));

            byte crt = 0; // byte buf
            int index = 0;
            for (int i = 0; i < fileSize; i++) {
                byte b = (byte)ins.read();
                TreeNode node = map.get(b);
                for (byte c: node.encode) {
                    crt = (byte)(crt|(c<<index));
                    index++;
                    if (index == 8) {
                        // write this byte to .zzip file and reset byte buf
                        outs.write(crt);
                        crt = 0;
                        index = 0;
                    }
                    compressedSize++;
                }
            }

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }
        return 0;
    }

    public static void main(String[] args) {
        return;
    }
}