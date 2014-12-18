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
public class HuffmanTree {

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
    public HuffmanTree(String fileName) {
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

            // build Huffman tree
            // count number of each byte
            int[] counts = new int[256];
            for (int i = 0; i < fileSize; i++) {
                byte b = (byte)ins.read();
                counts[(int)(b&0x0FF)]++;
            }
            this.root = buildTree(counts);
            
            ins.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }
    }

    /**
     * compressFile
     * | original size | treeNode num | tree nodes  | data  |
     * |   8 bytes     |    4bytes    |  {b, cnt}   | ..... |
     * @return decoded bit size
     */
    public long compressFile() {
        File file = new File(fileName);
        try {
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(fileName + ".zzip"));

            // write original file size
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(fileSize);
            outs.write(bBuf.array(), 0, 8);
            
            // write number of treeNodes
            bBuf = ByteBuffer.allocate(4);
            bBuf.putInt(nodeNum);
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
                    //System.out.println(c);
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
            if (index < 8) {
                outs.write(crt);
            }

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }
        return compressedSize;
    }

    /**
     * decompress File
     * @param zzipFile zzip file name
     * @return decompressed file size
     */
    public long decompress(String zzipFile) {
        if (zzipFile == null || zzipFile.length() < 6) {
            return 0;
        }
        // check file ending with ".zzip"
        if (!zzipFile.substring(zzipFile.length() - 5, zzipFile.length()).equals(".zzip")) {
            System.err.println("Error: not .zzip file");
            return 0;
        }

        try {
            // start decoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(zzipFile));

            // read original file size
            byte[] barray = new byte[8];
            ins.read(barray, 0, 8);
            fileSize = ByteBuffer.wrap(barray).getLong();
            
            // read number of treeNodes
            barray = new byte[4];
            ins.read(barray, 0, 4);
            nodeNum = ByteBuffer.wrap(barray).getInt();
            
            // start reading treeNodes and create the huffman tree
            int[] counts = new int[256];
            for (int i = 0; i < nodeNum; i++) {
                byte b = (byte)ins.read();
                ins.read(barray, 0, 4);
                int cnt = ByteBuffer.wrap(barray).getInt();
                counts[(int)(b&0x0FF)] = cnt;
            }
            this.root = buildTree(counts); // build tree
            
            // start decoding
            String originFileName = zzipFile.substring(0, zzipFile.length() - 5);
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(originFileName + ".out"));

            int crtSize = 0, index = 0;
            byte crtByte = (byte)ins.read();
            TreeNode tracer = root;
            while (crtSize < fileSize) {
                // trace from root to decode next byte
                while (tracer.left != null || tracer.right != null) {
                    if ((crtByte & (0x1<<index)) == 0) {
                        tracer = tracer.left;
                    }
                    else {
                        tracer = tracer.right;
                    }
                    index++;
                    if (index == 8) {
                        crtByte = (byte)ins.read();
                        index = 0;
                    }
                }
                // write a original byte
                outs.write(tracer.b);
                tracer = root;
                crtSize++;
            }

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }
        return fileSize;
    }

    /**
     * build huffman tree
     * @param counts count array of all bytes
     * @return tree root
     */
    private TreeNode buildTree(int[] counts) {
        // create tree nodes and put them in a min heap
        PriorityQueue<TreeNode> heap = new PriorityQueue<TreeNode>();
        for (int i = 0; i < 256; i++) {
            if (counts[i] > 0) {
                TreeNode node = new TreeNode((byte)(i&0xFF), counts[i]);
                heap.add(node);
            }
        }
        nodeNum = heap.size();

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
        return heap.poll(); // final node is root
    }


    public static void main(String[] args) {
        HuffmanTree huftree = new HuffmanTree("testFile");
        huftree.createHuffmanTree();
        huftree.compressFile();

        huftree = new HuffmanTree("testFile");
        huftree.decompress("testFile.zzip");

        return;
    }
}