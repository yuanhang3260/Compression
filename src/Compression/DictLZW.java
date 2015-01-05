package Compression;

import java.io.Serializable;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.StringBuilder;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import Compression.AbstractCompressor;

/**
 * LWZ dictionary compressing
 * @author Hang Yuan
 */
public class DictLZW extends AbstractCompressor {

    /**
     * dictionary record
     */
    class DictRecord {
        int encode;
        int len;
        String word = null;
        public DictRecord(int encode, int len, String word) {
            this.encode = encode;
            this.len = len;
            this.word = word;
        }
    }

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public DictLZW(String pathName) {
        super(pathName, "lzw");
    }

    /**
     * compress a file
     * | original size | encoded bytes |
     * |   8 bytes     |    ... ...    |
     * @return encoded size
     */
    @Override
    public long compress() {
        if (mode() != AbstractCompressor.Mode.Compress) {
            System.err.println("Error: Not in Compress mode");
            return 0;
        }

        // begin compressing
        System.out.println("Start Compressing ...");
        File file = new File(fileName);
        try {
            // start encoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));

            // read the orginal file and build the dictionary
            HashMap<String, DictRecord> map = new HashMap<String, DictRecord>();
            ArrayList<Integer> output = new ArrayList<Integer>();
            StringBuilder word = new StringBuilder();
            int nextNo = 255;
            byte nextByte = (byte)ins.read();
            word.append((char)nextByte);
            //System.out.println("read byte: " + (char)nextByte);
            for (int i = 0; i < fileSize; i++) {
                byte crtByte = nextByte;
                // process successive bytes
                if (i < fileSize - 1) {
                    nextByte = (byte)ins.read();
                    //System.out.println("read byte: " + (char)nextByte);
                    String oldWord = word.toString();
                    word.append((char)nextByte);
                    if (!map.containsKey(word.toString())) {
                        // write word to output
                        if (oldWord.length() == 1) {
                            output.add((int)crtByte);
                        }
                        else {
                            output.add(map.get(oldWord).encode);
                        }

                        // add current word as a new record into dictionary
                        map.put(word.toString(), 
                                new DictRecord(++nextNo, word.length(), word.toString()));

                        word.setLength(0);
                        word.append((char)nextByte);
                    }
                }
                else {
                    // reach EOF
                    if (word.length() == 1) {
                        output.add((int)crtByte);
                    }
                    else {
                        output.add(map.get(word.toString()).encode);
                    }
                }
            }

            // begin writing zip file
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(zipFileName));

            // write meta data
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(fileSize); // orignal file size
            outs.write(bBuf.array(), 0, 8);

            // write encoded bytes
            compressedSize = output.size();
            System.out.println("Compressed Size = " + compressedSize);
            for (Integer code: output) {
                //System.out.println("dumping: " + code);
                bBuf = ByteBuffer.allocate(4);
                bBuf.putInt(code); // orignal file size
                outs.write(bBuf.array(), 0, 4);
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
     * decompress file
     * @return original file size
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
            compressedSize = file.length();
            setCompressRate(((double)compressedSize) / fileSize);
            
            // output: decompressed file
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(fileName + ".out"));

            // initialize byte count and prob distribution
            int crtSize = 0;
            ArrayList<DictRecord> table = new ArrayList<DictRecord>();
            StringBuilder crtWord = new StringBuilder();
            byte[] lastEntry = null;
            while (crtSize < fileSize) {
                barray = new byte[4];
                ins.read(barray, 0, 4);
                int encode = ByteBuffer.wrap(barray).getInt();
                //System.out.println("read: " + encode);
                byte[] entry = null;
                if ((int)encode < 256) {
                    outs.write((byte)encode);
                    entry = new byte[1];
                    entry[0] = (byte)encode;
                    crtSize++;
                }
                else {
                    if (encode - 256 >= table.size()) {
                        entry = new byte[lastEntry.length + 1];
                        System.arraycopy(lastEntry, 0, entry, 0, lastEntry.length);
                        entry[lastEntry.length] = lastEntry[0];
                    }
                    else {
                        entry = table.get(encode - 256).word.getBytes();
                    }
                    outs.write(entry, 0, entry.length);
                    crtSize += entry.length;
                }
                if (lastEntry != null) {
                    // add (lastEntry + entry[0]) to dict
                    String newWord = new String(lastEntry) + (char)entry[0];
                    //System.out.println("adding " + newWord);
                    table.add(new DictRecord(table.size(), newWord.length(), newWord));
                }
                lastEntry = entry;
            }
            

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }

        System.err.println("Done: Decompressed File Size = " + fileSize + " bytes");
        return fileSize;
    }


    /**
     * check bits to Write
     * @return decompressed file size
     */
    private int checkBitsToWrite(double[] distri, int index,
                                 ArrayList<Integer> bitsToWrite) 
    {
        // compare with higer bound of this interval
        double rangeLow = distri[index];
        double rangeHigh = index == 255? 1 : distri[index + 1];
        int num1 = 0;
        while (true) {
            rangeLow *= 2;
            rangeHigh *= 2;
            if ((rangeLow >= 1 && rangeHigh  >= 1) || (rangeLow < 1 && rangeHigh < 1)) {
                bitsToWrite.add((int)rangeLow);
                rangeLow = rangeLow - (int)rangeLow; // remove non-faction part
                rangeHigh = rangeHigh - (int)rangeHigh;
                num1++;
            }
            else {
                break;
            }
        }

        return num1;
    }


    /**
     * Serach a value in distribution array
     * @return low range index
     */
    private int searchRange(double[] distri, double acc) {
        int start = -1, end = distri.length;
        // binary search
        while (start < end - 1) {
            int middle = (end + start) / 2;
            if (acc >= distri[middle]) {
                start = middle;
            }
            else {
                end = middle;
            }
        }
        return start;
    }    

    
}