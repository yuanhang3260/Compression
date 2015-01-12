#! /usr/bin/perl
# usage: ./testSingle.pl fileName

use File::Compare;

$testFileName = $ARGV[0];

system "java Compression.CompressTest $testFileName > /dev/null";
if (compare($testFileName, "$testFileName.out") != 0) {
    print "Error\n";
}
else {
	print "Success\n";
	unlink "$testFileName.art";
	unlink "$testFileName.out";
}