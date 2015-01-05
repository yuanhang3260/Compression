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
public class DicLZW extends AbstractCompressor {

    /**
     * dictionary record
     */
    class DicRecord {
        int encode;
        int len;
        String word = null;
        public DicRecord(int encode, int len, String word) {
            this.encode = encode;
            this.len = len;
            this.word = word;
        }
    }

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public DicLZW(String pathName) {
        super(pathName, "lzw");
    }

    /**
     * compress a file
     * | original size | number of records |     dictionary records     | encoded bytes |
     * |   8 bytes     |      8 bytes      | {encode, len, bytes[]} ... |    ... ...    |
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
            HashMap<String, DicRecord> map = new HashMap<String, DicRecord>();
            ArrayList<Byte> output = new ArrayList<Byte>();
            StringBuilder word = new StringBuilder();
            int nextNo = 0;
            byte nextByte = (byte)ins.read();
            for (int i = 0; i < fileSize; i++) {
                byte crtByte = nextByte;
                // add this byte to dictionary
                String crtByteAsString = String.valueOf(crtByte);
                if (!map.containsKey(crtByteAsString)) {
                    map.put(crtByteAsString, new DicRecord(++nextNo, 1, crtByteAsString));
                }
                // process successive bytes
                if (i < fileSize - 1) {
                    nextByte = (byte)ins.read();
                    String oldWord = word.toString();
                    word.append((char)nextByte);
                    if (!map.containsKey(word.toString())) {
                        // write word to output
                        for (Byte b: oldWord.getBytes()) {
                            output.add(b);
                        }
                        //output.addAll(Arrays.asList(oldWord.getBytes()));

                        // add current word as a new record into dictionary
                        map.put(word.toString(), 
                                new DicRecord(++nextNo, word.length(), word.toString()));

                        word.setLength(0);
                        word.append(nextByte);
                    }
                }
                else {
                    // reach EOF
                    for (Byte b: word.toString().getBytes()) {
                        output.add(b);
                    }
                }
            }

            // begin writing zip file
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(zipFileName));

            // write meta data
            ByteBuffer bBuf = ByteBuffer.allocate(16);
            bBuf.putLong(fileSize); // orignal file size
            bBuf.putInt(8, output.size()); // encoded byte size
            bBuf.putInt(12, map.size()); // number of dict records
            outs.write(bBuf.array(), 0, 16);

            // write dict records
            Iterator iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                DicRecord record = (DicRecord)entry.getValue();
                bBuf = ByteBuffer.allocate(8);
                bBuf.putInt(record.encode);
                bBuf.putInt(4, record.len);
                outs.write(bBuf.array(), 0, 8);
                for (int i = 0; i < record.len; i++) {
                    outs.write(record.word.getBytes(), 0, record.len);
                }
            }

            // write encoded bytes
            for (Byte b: output) {
                outs.write(b);
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
            int[] cnts = new int[256];
            Arrays.fill(cnts, 1);
            int total = 256;

            double[] distri = new double[256];
            for (int i = 1; i < 256; i++) {
                distri[i] = distri[i-1] + 1.0 / 256;
            }

            // start decoding
            int byteIndex = 0;
            int exp = 1;
            double acc = 0;
            byte crtByte = (byte)ins.read();
            int crtSize = 0, readSize = 3;
            ArrayList<Integer> bitsToWrite = new ArrayList<Integer>();
            while (crtSize < fileSize && readSize <= compressedSize) {
                int rangeLowIndex = searchRange(distri, acc);
                double rangeLow = distri[rangeLowIndex];
                double rangeHigh = rangeLowIndex == 255? 1 : distri[rangeLowIndex + 1];
                //System.out.println("rangeLowIndex = " + rangeLowIndex + ", [" + rangeLow + " " + rangeHigh + "]");
                if ((acc + 1.0 / exp) <= rangeHigh) {
                    // write a decoded byte
                    outs.write((byte)rangeLowIndex);
                    crtSize++;
                    System.out.println("\nproduce byte: " + (byte)rangeLowIndex);
                    int bitsWritten = checkBitsToWrite(distri, rangeLowIndex, bitsToWrite);
                    rangeLow = rangeLow * Math.pow(2, bitsWritten);
                    rangeLow = rangeLow - (int)rangeLow; // remove non-faction part
                    rangeHigh = rangeHigh * Math.pow(2, bitsWritten);
                    rangeHigh = rangeHigh - (int)rangeHigh;
                    acc = acc * Math.pow(2, bitsWritten);
                    acc = acc - (int)acc;
                    exp /= Math.pow(2, bitsWritten);
                    bitsToWrite.clear();
                    //System.out.println("normalized acc = " + acc);
                    // update cnts and distribution arrays
                    cnts[rangeLowIndex]++;
                    total++;
                    distri[0] = rangeLow;
                    for (int j = 1; j < 256; j++) {
                        distri[j] = distri[j-1] 
                                  + (1.0 * cnts[j-1] / total) * (rangeHigh - rangeLow);
                    }
                }
                else {
                    int bit = (crtByte>>byteIndex) & 0x1;
                    exp *= 2;
                    acc += (1.0*bit / exp);
                    System.out.printf("%d ", bit);
                    
                    byteIndex++;
                    if (byteIndex == 8) {
                        crtByte = (byte)ins.read();
                        //System.out.println("read byte: " + crtByte);
                        readSize++;
                        byteIndex = 0;
                    }
                }

            } //end while
            

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