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

    class Interval {
        double lowBound;
        double highBound;
        public Interval(double lowBound, double highBound) {
            this.lowBound = lowBound;
            this.highBound = highBound;
        }
    }

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

            double[] distri = new double[257];
            for (int i = 1; i < 257; i++) {
                distri[i] = distri[i-1] + 1.0 / 256;
            }

            byte crt = 0; // byte buf
            int byteIndex = 0; // bit index in byte buf
            Interval interval = new Interval(0, 1);
            ArrayList<Integer> bitsToWrite = new ArrayList<Integer>();
            for (int i = 0; i < fileSize; i++) {
                //System.out.println(i);
                byte b = (byte)ins.read();
                int index = (int)(b&0x0FF);
                //System.out.println("index = " + index);
                interval.lowBound = distri[index];
                interval.highBound = distri[index+1];
                //System.out.println("[" + interval.lowBound + " " + interval.highBound + "]");
                
                // try to write encoded bits if possible
                int bitsWritten = 0;
                try {
                    bitsWritten = checkBitsToWrite(interval, bitsToWrite);
                }
                catch (PrecisionException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                // System.out.printf("Got %d bits: [", bitsWritten);
                // for (Integer a: bitsToWrite) {
                //     System.out.printf("%d ", a);
                // }
                // System.out.println("\b]");
                // System.out.println("***[" + interval.lowBound + " " + interval.highBound + "]***");
                for (Integer a: bitsToWrite) {
                    crt = (byte)(crt|(a<<byteIndex));
                    byteIndex++;
                    if (byteIndex == 8) {
                        // write this byte to .art file and reset byte buf
                        outs.write(crt);
                        //System.out.println("dumping byte: " + Integer.toHexString(crt&0xFF).toUpperCase());
                        crt = 0;
                        byteIndex = 0;
                        compressedSize++;
                    }
                }
                bitsToWrite.clear();

                // update distribution and count arrays
                cnts[index]++;
                total++;
                distri[0] = interval.lowBound;
                for (int j = 1; j < 257; j++) {
                    distri[j] = distri[j-1] 
                                + (1.0 * cnts[j-1] / total) * (interval.highBound - interval.lowBound);
                }
                //System.out.println();
            }
            // write trailing bits
            if (interval.lowBound != interval.highBound) {
                double middle = (interval.lowBound + interval.highBound) / 2;
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

            double[] distri = new double[257];
            for (int i = 1; i < 257; i++) {
                distri[i] = distri[i-1] + 1.0 / 256;
            }

            // start decoding
            int byteIndex = 0;
            double exp = 1;
            double acc = 0;
            byte crtByte = (byte)ins.read();
            int crtSize = 0, readSize = 1;
            Interval interval = new Interval(0, 1);
            ArrayList<Integer> bitsToWrite = new ArrayList<Integer>();
            while (crtSize < fileSize && readSize <= compressedSize) {
                int lowBoundIndex = searchRange(distri, acc);
                //System.out.println(lowBoundIndex);
                
                interval.lowBound = distri[lowBoundIndex];
                interval.highBound = distri[lowBoundIndex + 1];
                //System.out.println("exp = " + exp + " acc = " + acc + " in [" + interval.lowBound + ", " + interval.highBound+ "]");
                
                if (interval.lowBound == interval.highBound) {
                    //System.out.println("lowBoundIndex = " + lowBoundIndex + " " + cnts[lowBoundIndex-1] + " " + total);
                }
                if ((acc + 1.0 / exp) <= interval.highBound) {
                    // write a decoded byte
                    outs.write((byte)lowBoundIndex);
                    crtSize++;
                    //System.out.println("produce byte: " + (byte)lowBoundIndex);
                    //System.out.println("acc = " + acc + ", [" + interval.lowBound + ", " + interval.highBound+ "]");
                    int bitsWritten = 0;
                    try {
                        bitsWritten = checkBitsToWrite(interval, bitsToWrite);
                    }
                    catch (PrecisionException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    //System.out.println(bitsWritten + " " + Math.pow(2, bitsWritten) + " acc = " + acc);
                    acc = powFraction(acc, bitsWritten);
                    exp /= Math.pow(2, bitsWritten);
                    //makeSystem.out.println("** acc = " + acc + ", [" + interval.lowBound + ", " + interval.highBound+ "]");
                    //System.out.println(acc + "  " + exp);
                    bitsToWrite.clear();
                    //System.out.println("normalized acc = " + acc);
                    // update cnts and distribution arrays
                    cnts[lowBoundIndex]++;
                    total++;
                    distri[0] = interval.lowBound;
                    for (int j = 1; j < 257; j++) {
                        distri[j] = distri[j-1] 
                                  + (1.0 * cnts[j-1] / total) * (interval.highBound - interval.lowBound);
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
                        //System.out.println("read byte: " + crtByte + ", readSize = " + readSize);
                        readSize++;
                        byteIndex = 0;
                    }
                }
                //System.out.println();
            } //end while
            

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }

        System.out.println("Done: Decompressed File Size = " + fileSize + " bytes");
        return fileSize;
    }

    /*
     * given a float number f and int num, return fraction(f * 2^num), 
     */
    private double powFraction(double f, int num) {
        for (int i = 0; i < num; i++) {
            f *= 2;
            f -= (int)f;
        }
        return f;
    }

    
    private int checkBitsToWrite(Interval interval, ArrayList<Integer> bitsToWrite) 
                throws PrecisionException
    {
        int num1 = 0;
        if (interval.highBound == 1) {
            while (interval.lowBound > 0) {
                interval.lowBound *= 2;
                int a = (int)interval.lowBound;
                if (a == 0) {
                    interval.lowBound /= 2;
                    break;
                }
                interval.lowBound -= a;
                bitsToWrite.add((int)a);
                num1++;
            }
            interval.highBound = 1;
            return num1;
        }

        if (interval.lowBound == interval.highBound) {
            //System.err.println("err");
            throw new PrecisionException("\nlowBound = highBound = " + interval.lowBound);
        }
        while (true) {
            //System.out.println("[" + lowBound + " " + highBound + "]");
            interval.lowBound *= 2;
            interval.highBound *= 2;
            if ( (interval.lowBound >= 1 && interval.highBound  >= 1) 
              || (interval.lowBound < 1 && interval.highBound < 1))
            {
                bitsToWrite.add((int)interval.lowBound);
                interval.lowBound = interval.lowBound - (int)interval.lowBound; // remove non-faction part
                interval.highBound = interval.highBound - (int)interval.highBound;
                num1++;
            }
            else {
                if (interval.highBound > 1) {
                    interval.lowBound /= 2;
                    interval.highBound /= 2;
                }
                else {
                    // higher bound has no trailing bits
                    bitsToWrite.add(0);
                    num1++;
                    while (interval.lowBound > 0) {
                        interval.lowBound *= 2;
                        int a = (int)interval.lowBound;
                        if (a == 0) {
                            interval.lowBound /= 2;
                            break;
                        }
                        interval.lowBound -= a;
                        bitsToWrite.add((int)a);
                        num1++;
                    }
                    interval.highBound = 1;
                }
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
        //System.out.println(distri[0] + " ~ " + distri[256] + ", search " + acc);
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
     *  range precision exception: lowBound = highBound
     */
    class PrecisionException extends Exception  
    {
        public PrecisionException(String msg)  
        {  
            super(msg);  
        }  
    }

}