#! /usr/bin/perl
use File::Compare;

$testFileName = "testBin";

print "Testing Arithmetic Encoder ...\n";
for ($count = 0; $count < 10000; $count++) {
    open(my $out, '>:raw', $testFileName);
    for ($size = 0; $size < 16384; $size++) {
        $randNum = int(rand() * 256);
        print $out pack('c', $randNum);
        # print "$randNum\n";
    }
    close($out);
    system "java -cp ./classes Compression.CompressTest $testFileName > /dev/null";
    if (compare($testFileName, "$testFileName.out") != 0) {
        print "Error\n";
        last;
    }
    print "Success: $count\n"
}

#unlink "$testFileName";
unlink "$testFileName.art"
