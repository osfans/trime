jni:
	gcc -shared -fpic key_table.cc  levers.cc  opencc.cc  rime.cc  rime_jni.cc -o librime_jni.so -lrime -lopencc -DCLASSNAME=\"Rime\" -DLIBRIME_VERSION=\"1.0.4\" -DTRIME_VERSION=\"3.1\" -DOPENCC_VERSION=\"1.0.5\" -L. -I../librime/src -I/usr/lib/jvm/default/include -I/usr/lib/jvm/default/include/linux
