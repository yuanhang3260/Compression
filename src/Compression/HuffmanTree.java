package Compression;

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
import Compression.AbstractCompressor;

/**
 * HuffmanTree
 * @author Hang Yuan
 */
public class HuffmanTree extends AbstractCompressor {

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

    TreeNode root = null;
    int nodeNum = 0;

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public HuffmanTree(String pathName) {
        super(pathName, "huf");
    }

    /**
     * compressFile
     * | original size | treeNode num | tree nodes  | data  |
     * |   8 bytes     |    4bytes    |  {b, cnt}   | ..... |
     * @return decoded bit size
     */
    @Override
    public long compress() {
        if (mode() != AbstractCompressor.Mode.Compress) {
            System.err.println("Error: Not in Compress mode");
            return 0;
        }
        
        // create huffman tree
        createHuffmanTree();

        // begin compressing
        System.out.println("Start Compressing ...");
        try {
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(zipFileName));

            // write original file size
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(fileSize);
            outs.write(bBuf.array(), 0, 8);
            
            // write number of treeNodes
            bBuf = ByteBuffer.allocate(4);
            bBuf.putInt(nodeNum);
            outs.write(bBuf.array(), 0, 4);
            
            compressedSize = 12;

            // traverse the huffman tree and write treeNodes to .huf file
            HashMap<Byte, TreeNode> map = new HashMap<Byte, TreeNode>();
            Stack<TreeNode> stack = new Stack<TreeNode>();
            stack.push(root);
            root.encode = new ArrayList<Byte>();
            while (!stack.isEmpty()) {
                TreeNode node = stack.pop();
                // write leave node to .huf file
                if (node.left == null && node.right == null) {
                    bBuf = ByteBuffer.allocate(5);
                    bBuf.put(node.b);
                    bBuf.putInt(1, node.count);
                    outs.write(bBuf.array(), 0, 5);
                    // add this leave to HashMap
                    map.put(node.b, node);
                    compressedSize += 5;
                }
                // push left and right child
                if (node.right != null) {
                    node.right.encode = new ArrayList<Byte>();
                    node.right.encode.addAll(node.encode);
                    node.right.encode.add((byte)1);
                    stack.push(node.right);
                }
                if (node.left != null) {
                    node.left.encode = new ArrayList<Byte>();
                    node.left.encode.addAll(node.encode);
                    node.left.encode.add((byte)0);
                    stack.push(node.left);
                }
            }

            // start encoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));

            byte crt = 0; // byte buf
            int byteIndex = 0;
            for (int i = 0; i < fileSize; i++) {
                byte b = (byte)ins.read();
                TreeNode node = map.get(b);
                for (byte c: node.encode) {
                    //System.out.println(c);
                    crt = (byte)(crt|(c<<byteIndex));
                    byteIndex++;
                    if (byteIndex == 8) {
                        // write this byte to .huf file and reset byte buf
                        outs.write(crt);
                        crt = 0;
                        byteIndex = 0;
                        compressedSize++;
                    }
                }
            }
            if (byteIndex < 8) {
                outs.write(crt);
                compressedSize++;
            }

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }

        // go to decompress mode
        setDecompressMode();
        setCompressRate(((double)compressedSize) / fileSize);
        System.out.println("Done: Compression Rate = " + 
                           String.format("%.2f", getCompressRate()*100) + "%\n");
        return compressedSize;
    }

    /**
     * decompress File
     * @return decompressed file size
     */
    @Override
    public long decompress() {
        // check file ending with ".huf"
        if (mode() != AbstractCompressor.Mode.Decompress) {
            System.err.println("Error: Not in Decompress mode");
            return 0;
        }

        try {
            System.out.println("Start Decompressing ...");
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(zipFileName));

            // read original file size
            byte[] barray = new byte[8];
            ins.read(barray, 0, 8);
            fileSize = ByteBuffer.wrap(barray).getLong();
            File file = new File(zipFileName);
            setCompressRate(((double)file.length()) / fileSize);
            
            // read number of treeNodes
            barray = new byte[4];
            ins.read(barray, 0, 4);
            nodeNum = ByteBuffer.wrap(barray).getInt();
            
            // read leave nodes and recover the huffman tree
            int[] counts = new int[256];
            for (int i = 0; i < nodeNum; i++) {
                byte b = (byte)ins.read();
                ins.read(barray, 0, 4);
                int cnt = ByteBuffer.wrap(barray).getInt();
                counts[(int)(b&0x0FF)] = cnt;
            }
            this.root = buildTree(counts); // build tree
            
            // start decoding
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(fileName + ".out"));

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

        System.out.println("Done: Decompressed File Size = " + fileSize + " bytes");
        return fileSize;
    }


    /**
     * build huffman tree
     * @param counts count array of all bytes
     * @return tree root
     */
    public TreeNode buildTree(int[] counts) {
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


    /**
     * create Huffman Tree
     */
    private void createHuffmanTree() {
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

}