package Compression;

import java.util.Base64;
import Compression.CompressService;
import Compression.Compressors;

public class CompressTest {
    public static void main(String[] args) throws Exception {
        CompressService compressor = Compressors.newDictLZW("a.out");

        compressor.compress();
        compressor.decompress();

        // Base64.Encoder encoder = Base64.getEncoder();
        // Base64.Decoder decoder = Base64.getDecoder();
        // byte[] b = new byte[1];
        // b[0] = -128;
        // System.out.println(b[0]);
        // String teststr = new String(encoder.encode(b));
        // byte[] re = decoder.decode(teststr.getBytes());
        // System.out.println(re.length + " " + re[0]);

        // try {
        //     BufferedOutputStream outs = 
        //         new BufferedOutputStream(new FileOutputStream("out.txt"));
        //     outs.write(-128);
        //     outs.close();
        // }
        // catch (IOException e) {
        //    e.printStackTrace();
        // }

        return;
    }
}
