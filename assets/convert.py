#!/usr/bin/env python3
import fileinput,re
for line in fileinput.input():
  line = line.rstrip()
  if "0x" in line:
    i=line.index("0x")
    line=line[:i]+"0x"+line[i+6:i+8]+line[i+4:i+6]+line[i+2:i+4]+line[i+8:]
  print(line)
