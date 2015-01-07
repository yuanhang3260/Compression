package Compression;

import java.io.Serializable;
import java.lang.Thread;
import java.util.Arrays;
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
 * Arithmatic Coding
 * @author Hang Yuan
 */
public class ArithCoder extends AbstractCompressor {

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public ArithCoder(String pathName) {
        super(pathName, "art");
    }

    /**
     * compress file
     * | original size | data  |
     * |   8 bytes     | ..... |
     * @return decoded bit size
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
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(zipFileName));

            // write original file size
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(fileSize);
            outs.write(bBuf.array(), 0, 8);
            
            // start encoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));

            // byte count and prob distribution
            int[] cnts = new int[256];
            Arrays.fill(cnts, 1);
            int total = 256;

            double[] distri = new double[256];
            for (int i = 1; i < 256; i++) {
                distri[i] = distri[i-1] + 1.0 / 256;
            }

            byte crt = 0; // byte buf
            int byteIndex = 0; // bit index in byte buf
            double rangeLow = 0, rangeHigh = 1;
            ArrayList<Integer> bitsToWrite = new ArrayList<Integer>();
            for (int i = 0; i < fileSize; i++) {
                System.out.println(i);
                byte b = (byte)ins.read();
                int index = (int)(b&0x0FF);
                
                // try to write encoded bits if possible
                int bitsWritten = 0;
                try {
                    bitsWritten = checkBitsToWrite(distri, index, bitsToWrite);
                }
                catch (PrecisionException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                //System.out.printf("Got %d bits: [", bitsWritten);
                // for (Integer a: bitsToWrite) {
                //     System.out.printf("%d ", a);
                // }
                // System.out.println("\b]");
                //System.out.println("[" + rangeLow + " " + rangeHigh + "]");
                for (Integer a: bitsToWrite) {
                    crt = (byte)(crt|(a<<byteIndex));
                    byteIndex++;
                    if (byteIndex == 8) {
                        // write this byte to .art file and reset byte buf
                        outs.write(crt);
                        //System.out.println("dumping byte: " + crt);
                        crt = 0;
                        byteIndex = 0;
                        compressedSize++;
                    }
                }
                bitsToWrite.clear();

                // update range low and high
                rangeLow = distri[index];
                rangeHigh = index == 255? 1 : distri[index + 1];
                rangeLow = rangeLow * Math.pow(2, bitsWritten);
                rangeLow = rangeLow - (int)rangeLow; // remove non-faction part
                rangeHigh = rangeHigh * Math.pow(2, bitsWritten);
                rangeHigh = rangeHigh - (int)rangeHigh;

                // update distribution and count arrays
                cnts[index]++;
                total++;
                distri[0] = rangeLow;
                for (int j = 1; j < 256; j++) {
                    distri[j] = distri[j-1] 
                                + (1.0 * cnts[j-1] / total) * (rangeHigh - rangeLow);
                }
            }
            // write trailing bits
            if (rangeLow != rangeHigh) {
                double middle = (rangeLow + rangeHigh) / 2;
                while (middle > 0) {
                    middle *= 2;
                    int a = (int)middle;
                    middle -= a;
                    crt = (byte)(crt|(a<<byteIndex));
                    byteIndex++;
                    if (byteIndex == 8) {
                        // write this byte to .art file and reset byte buf
                        outs.write(crt);
                        //System.out.println("dumping byte: " + crt);
                        crt = 0;
                        byteIndex = 0;
                        compressedSize++;
                    }
                }
            }
            // write the last imcomplete byte
            if (byteIndex < 8) {
                outs.write(crt);
                //System.out.println("dumping last byte: " + crt);
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
     * decompress file
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
                    //System.out.println("\nproduce byte: " + (byte)rangeLowIndex);
                    int bitsWritten = 0;
                    try {
                        bitsWritten = checkBitsToWrite(distri, rangeLowIndex, bitsToWrite);
                    }
                    catch (PrecisionException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
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
                    //System.out.printf("%d ", bit);
                    
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
                                 ArrayList<Integer> bitsToWrite) throws PrecisionException
    {
        // compare with higer bound of this interval
        double rangeLow = distri[index];
        double rangeHigh = index == 255? 1 : distri[index + 1];
        if (rangeLow == rangeHigh) {
            //System.err.println("err");
            throw new PrecisionException("\nrangeLow = rangeHigh = " + rangeLow);
        }
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


    /**
     *  range precision exception: rangeLow = rangeHigh
     */
    class PrecisionException extends Exception  
    {
        public PrecisionException(String msg)  
        {  
            super(msg);  
        }  
    }

}