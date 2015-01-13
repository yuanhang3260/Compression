SOURCE=$(shell find ./src -name '*.java')
CLASSES=$(subst .java,.class,$(SOURCE))

.PHONY: all code clean

all: code

code: $(SOURCE)
	javac -sourcepath ./src -d ./classes $^

clean:
	find ./classes -name '*'.class -exec rm -f {} ';'
	rm -rf Compression.jar
	
jar:
	jar cfm ./Compression.jar manifest.mf -C ./classes .
	
# ensure the next line is always the last line in this file.
# vi:noet

